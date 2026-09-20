package club.minnced.opus.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class NativeUtil {
    private NativeUtil() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void loadLibraryFromJar(String path) throws IOException {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("The path has to be absolute (start with '/').");
        }
        String[] parts = path.split("/");
        String filename = parts.length > 1 ? parts[parts.length - 1] : null;
        String prefix = "";
        String suffix = null;
        if (filename != null) {
            parts = filename.split("\\.", 2);
            prefix = parts[0];
            String string = suffix = parts.length > 1 ? "." + parts[parts.length - 1] : null;
        }
        if (filename == null || prefix.length() < 3) {
            throw new IllegalArgumentException("The filename has to be at least 3 characters long.");
        }
        File temp = File.createTempFile(prefix, suffix);
        temp.deleteOnExit();
        if (!temp.exists()) {
            throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");
        }
        byte[] buffer = new byte[1024];
        InputStream is = NativeUtil.class.getResourceAsStream(path);
        if (is == null) {
            throw new FileNotFoundException("File " + path + " was not found inside JAR.");
        }
        FileOutputStream os = new FileOutputStream(temp);
        try {
            int readBytes;
            while ((readBytes = is.read(buffer)) != -1) {
                ((OutputStream)os).write(buffer, 0, readBytes);
            }
        }
        finally {
            ((OutputStream)os).close();
            is.close();
        }
        System.load(temp.getAbsolutePath());
    }
}

