/*
 * Copyright (c) 2023-2024 Works Applications Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.worksap.nlp.sudachi;

import java.io.IOException;
import java.nio.CharBuffer;

public class IOTools {
    private IOTools() {
        // forbid instantiation
    }

    /**
     * Read as much as possible from the readable to the result buffer. Use this to
     * make sure that the buffer is fulfilled or no text left unread.
     *
     * @param readable
     *            input readable
     * @param result
     *            buffer to read into
     * @return number of read characters
     * @throws IOException
     *             when read operation fails
     */
    public static int readAsMuchAsCan(Readable readable, CharBuffer result) throws IOException {
        int totalRead = 0;
        while (result.hasRemaining()) {
            int read = readable.read(result);
            if (read < 0) {
                if (totalRead == 0) {
                    return -1;
                } else {
                    return totalRead;
                }
            }
            totalRead += read;
        }
        return totalRead;
    }

    /**
     * Wrapper class for Readable, that uses {@link #readAsMuchAsCan} to read and
     * guarantees that the last character read is not a high surrogate unless it is
     * the last one in the readable.
     */
    public static class SurrogateAwareReadable implements Readable {
        private Readable readable;
        char lastTrailingHighSurrogate;

        SurrogateAwareReadable(Readable input) {
            this.readable = input;
        }

        @Override
        public int read(CharBuffer cb) throws IOException {
            boolean trailingKept = false;
            if (lastTrailingHighSurrogate != 0) {
                cb.append(lastTrailingHighSurrogate);
                lastTrailingHighSurrogate = 0;
                trailingKept = true;
            }

            int nread = IOTools.readAsMuchAsCan(readable, cb);
            if (nread < 0) {
                if (!trailingKept) {
                    return -1;
                }
                // the last char in the readable is a high surrogate and there is nothing we can
                // do.
                return 1;
            }
            if (trailingKept) {
                nread += 1;
            }

            char lastChar = cb.get(cb.position() - 1);
            if (Character.isHighSurrogate(lastChar)) {
                lastTrailingHighSurrogate = lastChar;
                cb.position(cb.position() - 1);
                nread -= 1;
            }
            return nread;
        }
    }
}
