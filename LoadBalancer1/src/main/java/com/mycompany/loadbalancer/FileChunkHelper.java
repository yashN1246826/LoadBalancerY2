package com.mycompany.loadbalancer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class FileChunkHelper {

    public static List<File> splitFile(File file, long chunkSize) {
        List<File> chunkFiles = new ArrayList<>();
        byte[] buffer = new byte[(int) chunkSize];
        
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            int partNumber = 0;
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) > 0) {
                partNumber++;
                File chunkFile = new File(file.getParent(), file.getName() + "_Chunk" + (char) ('A' + partNumber - 1));
                try (FileOutputStream fos = new FileOutputStream(chunkFile);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                    bos.write(buffer, 0, bytesRead);
                }
                LoggerHelper.log("FILE_CHUNKING", "✅ Created chunk: " + chunkFile.getName());
                chunkFiles.add(chunkFile);
            }

        } catch (IOException e) {
            LoggerHelper.log("ERROR", "❌ Error while splitting file: " + e.getMessage());
        }

        return chunkFiles;
    }
}
