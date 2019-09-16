package io.jenkins.plugins.pipeline_elasticsearch_logs;

import hudson.console.LineTransformationOutputStream;
import hudson.model.BuildListener;
import net.sf.json.JSONObject;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ElasticSearchSender implements BuildListener, Closeable
{
  private static final String EVENT_PREFIX_BUILD = "build";

  private static final String EVENT_PREFIX_NODE = "node";

  private static final Logger LOGGER = Logger.getLogger(ElasticSearchSender.class.getName());

  private static final long serialVersionUID = 1;

  private transient @CheckForNull PrintStream logger;
  private final @CheckForNull NodeInfo nodeInfo;

  protected transient ElasticSearchWriter writer;
  protected final ElasticSearchRunConfiguration config;
  protected String eventPrefix;

  public ElasticSearchSender(@CheckForNull NodeInfo nodeInfo, @Nonnull ElasticSearchRunConfiguration config) throws IOException
  {
    this.nodeInfo = nodeInfo;
    this.config = config;
    if (nodeInfo != null)
    {
      eventPrefix = EVENT_PREFIX_NODE;
    }
    else
    {
      eventPrefix = EVENT_PREFIX_BUILD;
    }
  }

  public PrintStream getWrappedLogger(@CheckForNull OutputStream logger) {
    try {
      return new PrintStream(new ElasticSearchOutputStream(logger), false, "UTF-8");
    } catch (UnsupportedEncodingException x) {
      throw new AssertionError(x);
    }
  }


  @Override
  public PrintStream getLogger()
  {
    if (logger == null)
    {
      logger = getWrappedLogger(null);
    }
    return logger;
  }

  @Override
  public void close() throws IOException
  {
    logger = null;
    writer = null;
  }

  private ElasticSearchWriter getElasticSearchWriter() throws IOException
  {
    if (writer == null)
    {
      writer = config.createWriter();
    }
    return writer;
  }

  private class ElasticSearchOutputStream extends LineTransformationOutputStream
  {
    private static final String EVENT_TYPE_MESSAGE = "Message";
    private @CheckForNull OutputStream forwardingLogger;

    public ElasticSearchOutputStream(@CheckForNull OutputStream logger) {
      this.forwardingLogger = logger;
    }

    public ElasticSearchOutputStream() {
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException
    {
      if (forwardingLogger != null) {
        forwardingLogger.write(b,0,len);
      }
      Map<String, Object> data = config.createData();

      ConsoleNotes.parse(b, len, data, config.isSaveAnnotations());
      data.put(ElasticSearchGraphListener.EVENT_TYPE, eventPrefix + EVENT_TYPE_MESSAGE);
      if (nodeInfo != null)
      {
        nodeInfo.appendNodeInfo(data);
      }

      LOGGER.log(Level.FINEST, "Sending data: {0}", JSONObject.fromObject(data).toString());
      getElasticSearchWriter().push(JSONObject.fromObject(data).toString());
    }
  }
}
