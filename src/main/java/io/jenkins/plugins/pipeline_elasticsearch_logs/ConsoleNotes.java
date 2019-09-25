/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 * Copyright 2019 SAP SE
 *
 * Source: https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/66f4d48a5560e518646d04739c3d2f7224e9dc93/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/ConsoleNotes.java
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.pipeline_elasticsearch_logs;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import hudson.console.ConsoleNote;

/**
 * Utilities for extracting and reinserting {@link ConsoleNote}s.
 */
class ConsoleNotes {

    private static final String MESSAGE_KEY = "message";
    private static final String ANNOTATIONS_KEY = "annotations";
    private static final String POSITION_KEY = "position";
    private static final String NOTE_KEY = "note";

    static void parse(byte[] b, int len, Map<String, Object> data, boolean saveAnnotations) {
        assert len > 0 && len <= b.length;

        assert data != null;

        int eol = len;
        while (eol > 0) {
            byte c = b[eol - 1];
            if (c == '\n' || c == '\r') {
                eol--;
            } else {
                break;
            }
        }

        String line = new String(b, 0, eol, StandardCharsets.UTF_8);

        // Would be more efficient to do searches at the byte[] level, but too much
        // bother for now,
        // especially since there is no standard library method to do offset searches
        // like String has.
        if (!line.contains(ConsoleNote.PREAMBLE_STR)) {
            // Shortcut for the common case that we have no notes.
            data.put(MESSAGE_KEY, line);
        } else {
            StringBuilder buf = new StringBuilder();
            List<Map<String, Object>> annotations = new ArrayList<>();
            int pos = 0;
            while (true) {
                int preamble = line.indexOf(ConsoleNote.PREAMBLE_STR, pos);
                if (preamble == -1) {
                    break;
                }
                int endOfPreamble = preamble + ConsoleNote.PREAMBLE_STR.length();
                int postamble = line.indexOf(ConsoleNote.POSTAMBLE_STR, endOfPreamble);
                if (postamble == -1) {
                    // Malformed; stop here.
                    break;
                }
                buf.append(line, pos, preamble);
                annotations.add(ImmutableMap.of(POSITION_KEY, buf.length(), NOTE_KEY,
                        line.substring(endOfPreamble, postamble)));
                pos = postamble + ConsoleNote.POSTAMBLE_STR.length();
            }
            buf.append(line, pos, line.length()); // append tail
            data.put(MESSAGE_KEY, buf.toString());
            if (saveAnnotations) {
                data.put(ANNOTATIONS_KEY, annotations);
            }
        }
    }

    static void write(Writer w, Map<String, Object> source) throws IOException {
        if (source == null)
            throw new NullPointerException("source is null");
        String message = (String) source.get(MESSAGE_KEY);
        Object annotations = source.get(ANNOTATIONS_KEY);
        if (annotations == null || !(annotations instanceof ArrayList)) {
            w.write(message);
        } else {
            int pos = 0;
            for (Object o : (ArrayList<Object>) annotations) {
                Map<String, Object> annotation = (Map<String, Object>) o;
                int position = (Integer) annotation.get(POSITION_KEY);
                String note = (String) annotation.get(NOTE_KEY);
                w.write(message, pos, position - pos);
                w.write(ConsoleNote.PREAMBLE_STR);
                w.write(note);
                w.write(ConsoleNote.POSTAMBLE_STR);
                pos = position;
            }
            w.write(message, pos, message.length() - pos);
        }
        w.write('\n');
    }

    private ConsoleNotes() {
    }

}