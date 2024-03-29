package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.input.NullReader;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner.Executable;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.log.BrokenLogStorage;
import org.jenkinsci.plugins.workflow.log.ConsoleAnnotators;
import org.jenkinsci.plugins.workflow.log.LogStorage;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.console.ConsoleNote;
import hudson.console.LineTransformationOutputStream;
import hudson.model.BuildListener;
import hudson.model.TaskListener;

class ElasticsearchLogStorage implements LogStorage {

    final static Logger LOGGER = Logger.getLogger(ElasticsearchLogStorage.class.getName());

    private static final Map<File, ElasticsearchLogStorage> openStorages = Collections.synchronizedMap(new HashMap<>());

    private final File log;
    private File index;
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "actually it is always accessed within the monitor")
    private FileOutputStream os;
    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "we only care about synchronizing writes")
    private OutputStream bos;
    private Writer indexOs;
    private String lastId;

    private ElasticsearchRunConfig config;

    public static synchronized LogStorage forFile(ElasticsearchRunConfig config, File log) {
        return openStorages.computeIfAbsent(log, k -> new ElasticsearchLogStorage(config, log));
    }

    ElasticsearchLogStorage(ElasticsearchRunConfig config, File log) {
        this.config = config;
        this.log = log;
        this.index = new File(log + "-index");
    }

    private synchronized void open() throws IOException {
        if (os == null) {
            os = new FileOutputStream(log, true);
            bos = new GCFlushedOutputStream(new DelayBufferedOutputStream(os));
            if (index.isFile()) {
                try (BufferedReader r = Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8)) {
                    // TODO would be faster to scan the file backwards for the penultimate \n, then
                    // convert the byte sequence from there to EOF to UTF-8 and set lastId
                    // accordingly
                    String lastLine = null;
                    while (true) {
                        // Note that BufferedReader tolerates final lines without a line separator, so
                        // if for some reason the last write has been truncated this result could be
                        // incorrect.
                        // In practice this seems unlikely since we explicitly flush after the newline,
                        // so we should be sending a single small block to the filesystem to persist.
                        // Anyway at worst the result would be a (perhaps temporarily) incorrect line →
                        // step mapping, which is tolerable for one step of one build, and barely
                        // affects the overall build log.
                        String line = r.readLine();
                        if (line == null) {
                            break;
                        } else {
                            lastLine = line;
                        }
                    }
                    if (lastLine != null) {
                        int space = lastLine.indexOf(' ');
                        lastId = space == -1 ? null : lastLine.substring(space + 1);
                    }
                }
            }
            indexOs = new OutputStreamWriter(new FileOutputStream(index, true), StandardCharsets.UTF_8);
        }
    }

    @Override
    public BuildListener overallListener() throws IOException, InterruptedException {
        IndexOutputStream out = new IndexOutputStream(null);
        return new ElasticsearchSender(null, config, out);
    }

    @Override
    public TaskListener nodeListener(FlowNode node) throws IOException, InterruptedException {
        NodeInfo nodeInfo = new NodeInfo(node);
        IndexOutputStream out = new IndexOutputStream(nodeInfo.nodeId);
        return new ElasticsearchSender(nodeInfo, config, out);
    }

    // Method copied from FileLogStorage of workflow-api plugin
    private void checkId(String id) throws IOException {
        assert Thread.holdsLock(this);
        if (!Objects.equals(id, lastId)) {
            bos.flush();
            long pos = os.getChannel().position();
            if (id == null) {
                indexOs.write(pos + "\n");
            } else {
                indexOs.write(pos + " " + id + "\n");
            }
            // Could call FileChannel.force(true) like hudson.util.FileChannelWriter does
            // for AtomicFileWriter,
            // though making index-log writes slower is likely a poor tradeoff for slightly
            // more reliable log display,
            // since logs are often never read and this is transient data rather than
            // configuration or valuable state.
            indexOs.flush();
            lastId = id;
        }
    }

    // Copied from FileLogStorage of workflow-api plugin
    private final class IndexOutputStream extends LineTransformationOutputStream {

        private final String id;

        IndexOutputStream(String id) throws IOException {
            this.id = id;
            open();
        }

        @Override
        protected void eol(byte[] b, int len) throws IOException {
            synchronized (ElasticsearchLogStorage.this) {
                checkId(id);
                if (!config.isWriteAnnotationsToLogFile()) {
                    String line = new String(b, 0, len, StandardCharsets.UTF_8);
                    line = ConsoleNote.removeNotes(line);
                    bos.write(line.getBytes(StandardCharsets.UTF_8));
                } else {
                    bos.write(b, 0, len);
                }
            }
        }

        @Override
        public void flush() throws IOException {
            bos.flush();
        }

        @Override
        public void close() throws IOException {
            if (id == null) {
                openStorages.remove(log);

                IOException firstException = null;
                try {
                    bos.flush();
                }
                catch (IOException ex) {
                    firstException = ex;
                }

                try {
                    bos.close();
                }
                catch (IOException ex) {
                    if (firstException != null) firstException = ex;
                }

                try {
                    indexOs.flush();
                }
                catch (IOException ex) {
                    if (firstException != null) firstException = ex;
                }

                try {
                    indexOs.close();
                }
                catch (IOException ex) {
                    if (firstException != null) firstException = ex;
                }

                if (firstException != null) throw firstException;
            }
        }
    }

    // Method copied from FileLogStorage of workflow-api plugin
    private void maybeFlush() {
        if (bos != null) {
            try {
                bos.flush();
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, "failed to flush " + log, x);
            }
        }
    }

    // Method copied from FileLogStorage of workflow-api plugin
    @Override
    public AnnotatedLargeText<Executable> overallLog(Executable build, boolean complete) {
        maybeFlush();
        return new AnnotatedLargeText<FlowExecutionOwner.Executable>(log, StandardCharsets.UTF_8, complete, build) {
            @Override public long writeHtmlTo(long start, Writer w) throws IOException {
                try (BufferedReader indexBR = index.isFile() ? Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8) : new BufferedReader(new NullReader(0))) {
                    ConsoleAnnotationOutputStream<FlowExecutionOwner.Executable> caos = new ConsoleAnnotationOutputStream<>(w, ConsoleAnnotators.createAnnotator(build), build, StandardCharsets.UTF_8);
                    long r = this.writeRawLogTo(start, new FilterOutputStream(caos) {
                        // To insert startStep/endStep annotations into the overall log, we need to simultaneously read index-log.
                        // We use the standard LargeText.FileSession to get the raw log text (we need not think about ConsoleNote here), having seeked to the start position.
                        // Then we read index-log in order, looking for transitions from one step to the next (or to or from non-step overall output).
                        // Whenever we are about to write a byte which is at a boundary, or if there is a boundary at EOF, the HTML annotations are injected into the output;
                        // the read of index-log is advanced lazily (it is not necessary to have the whole mapping in memory).
                        long lastTransition = -1;
                        boolean eof; // NullReader is strict and throws IOException (not EOFException) if you read() again after having already gotten -1
                        String lastId;
                        long pos = start;
                        boolean hadLastId;
                        @Override public void write(int b) throws IOException {
                            while (lastTransition < pos && !eof) {
                                String line = indexBR.readLine();
                                if (line == null) {
                                    eof = true;
                                    break;
                                }
                                int space = line.indexOf(' ');
                                try {
                                    lastTransition = Long.parseLong(space == -1 ? line : line.substring(0, space));
                                } catch (NumberFormatException x) {
                                    LOGGER.warning("Ignoring corrupt index file " + index);
                                }
                                lastId = space == -1 ? null : line.substring(space + 1);
                            }
                            if (pos == lastTransition) {
                                if (hadLastId) {
                                    w.write(LogStorage.endStep());
                                }
                                hadLastId = lastId != null;
                                if (lastId != null) {
                                    w.write(LogStorage.startStep(lastId));
                                }
                            }
                            super.write(b);
                            pos++;
                        }
                        @Override public void flush() throws IOException {
                            if (lastId != null) {
                                w.write(LogStorage.endStep());
                            }
                            super.flush();
                        }
                    });
                    ConsoleAnnotators.setAnnotator(caos.getConsoleAnnotator());
                    return r;
                }
            }
        };    }

    // Method copied from FileLogStorage of workflow-api plugin
    @Override
    public AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean complete) {
        maybeFlush();
        String id = node.getId();
        try (ByteBuffer buf = new ByteBuffer();
             RandomAccessFile raf = new RandomAccessFile(log, "r");
             BufferedReader indexBR = index.isFile() ? Files.newBufferedReader(index.toPath(), StandardCharsets.UTF_8) : new BufferedReader(new NullReader(0))) {
            // Check this _before_ reading index-log to reduce the chance of a race condition resulting in recent content being associated with the wrong step:
            long end = raf.length();
            // To produce just the output for a single step (again we do not need to pay attention to ConsoleNote here since AnnotatedLargeText handles it),
            // index-log is read looking for transitions that pertain to this step: beginning or ending its content, including at EOF if applicable.
            // (Other transitions, such as to or from unrelated steps, are irrelevant).
            // Once a start and end position have been identified, that block is copied to a memory buffer.
            String line;
            long pos = -1; // -1 if not currently in this node, start position if we are
            while ((line = indexBR.readLine()) != null) {
                int space = line.indexOf(' ');
                long lastTransition = -1;
                try {
                    lastTransition = Long.parseLong(space == -1 ? line : line.substring(0, space));
                } catch (NumberFormatException x) {
                    LOGGER.warning("Ignoring corrupt index file " + index);
                    // If index-log is corrupt for whatever reason, we given up on this step in this build;
                    // there is no way we would be able to produce accurate output anyway.
                    // Note that NumberFormatException is nonfatal in the case of the overall build log:
                    // the whole-build HTML output always includes exactly what is in the main log file,
                    // at worst with some missing or inaccurate startStep/endStep annotations.
                    continue;
                }
                if (pos == -1) {
                    if (space != -1 && line.substring(space + 1).equals(id)) {
                        pos = lastTransition;
                    }
                } else if (lastTransition > pos) {
                    raf.seek(pos);
                    if (lastTransition > pos + Integer.MAX_VALUE) {
                        throw new IOException("Cannot read more than 2Gib at a time"); // ByteBuffer does not support it anyway
                    }
                    // Could perhaps be done a bit more efficiently with FileChannel methods,
                    // at least if org.kohsuke.stapler.framework.io.ByteBuffer were replaced by java.nio.[Heap]ByteBuffer.
                    // The overall bottleneck here is however the need to use a memory buffer to begin with:
                    // LargeText.Source/Session are not public so, pending improvements to Stapler,
                    // we cannot lazily stream per-step content the way we do for the overall log.
                    // (Except perhaps by extending ByteBuffer and then overriding every public method!)
                    // LargeText also needs to be improved to support opaque (non-long) cursors
                    // (and callers such as progressiveText.jelly and Blue Ocean updated accordingly),
                    // which is a hard requirement for efficient rendering of cloud-backed logs,
                    // though for this implementation we do not need it since we can work with byte offsets.
                    byte[] data = new byte[(int) (lastTransition - pos)];
                    raf.readFully(data);
                    buf.write(data);
                    pos = -1;
                } // else some sort of mismatch
            }
            if (pos != -1 && /* otherwise race condition? */ end > pos) {
                // In case the build is ongoing and we are still actively writing content for this step,
                // we will hit EOF before any other transition. Otherwise identical to normal case above.
                raf.seek(pos);
                if (end > pos + Integer.MAX_VALUE) {
                    throw new IOException("Cannot read more than 2Gib at a time");
                }
                byte[] data = new byte[(int) (end - pos)];
                raf.readFully(data);
                buf.write(data);
            }
            return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, complete, node);
        } catch (IOException x) {
            return new BrokenLogStorage(x).stepLog(node, complete);
        }
    }

    @Override
    public File getLogFile(Executable build, boolean complete) {
        return log;
    }
}
