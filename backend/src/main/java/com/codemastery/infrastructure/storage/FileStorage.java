package com.codemastery.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

/** CT09 shared infrastructure port; callers perform admission before accessing storage. */
public interface FileStorage {
    void store(String objectKey, InputStream content, long contentLength, String mediaType);

    StoredObject open(String objectKey);

    void delete(String objectKey);

    record StoredObject(long contentLength, String mediaType, InputStream content)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }

    class FileStorageException extends RuntimeException {
        public FileStorageException() {
            super("File storage operation failed.");
        }
    }

    final class ObjectNotFoundException extends FileStorageException { }
}
