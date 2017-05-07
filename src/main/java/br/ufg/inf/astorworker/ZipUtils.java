package br.ufg.inf.astorworker;
import java.util.zip.*;
import java.io.*;
import java.util.List;
import java.util.ArrayList;


public class ZipUtils {
	private static final int BUFFER = 2048;
    private static List<String> fileList;
    private static ZipUtils instance;

    private ZipUtils(){
        fileList  = new ArrayList<String>();
    }

    public static ZipUtils getInstance(){
        if(instance == null)
            instance = new ZipUtils();
        return instance;
    }

public static File receiveFolder(String projectName, InputStream is) throws IOException {
        BufferedOutputStream dest = null;
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
        ZipEntry entry;
        String mainFolder = null;


        while((entry = zis.getNextEntry()) != null) {

            String fileName = entry.getName().split("src/")[1];
            mainFolder = fileName.split("/")[0];

            int count;
            byte data[] = new byte[BUFFER];
            
            File temp = new File("workDir/AstorWorker-"+projectName+"/variants/"+fileName);
            temp.getParentFile().mkdirs();

            FileOutputStream fos = new FileOutputStream("workDir/AstorWorker-"+projectName+"/variants/"+fileName);
            dest = new BufferedOutputStream(fos, BUFFER);
            while ((count = zis.read(data, 0, BUFFER)) != -1) 
                dest.write(data, 0, count);
            
            dest.flush();
            dest.close();
        }
        
        return new File("workDir/AstorWorker-"+projectName+"/variants/"+mainFolder);
    }

    public static File receiveInitialProject(String projectName, InputStream is) throws IOException {
        BufferedOutputStream dest = null;
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is));
        ZipEntry entry;
        

        while((entry = zis.getNextEntry()) != null) {
            String fileName = entry.getName().split("Astor4AndroidMain-"+projectName+"/")[1];

            int count;
            byte data[] = new byte[BUFFER];
            
            File temp = new File("workDir/AstorWorker-"+projectName+"/"+fileName);
            temp.getParentFile().mkdirs();

            FileOutputStream fos = new FileOutputStream("workDir/AstorWorker-"+projectName+"/"+fileName);
            dest = new BufferedOutputStream(fos, BUFFER);
            while ((count = zis.read(data, 0, BUFFER)) != -1) 
                dest.write(data, 0, count);
            
            dest.flush();
            dest.close();
        }

        return new File("workDir/AstorWorker-"+projectName+"/"+projectName);
    }

	private static List<String> generateFileList(String source, File node) {
        // add file only
        if (node.isFile()) {
            fileList.add(generateZipEntry(source, node.toString()));
        }

        if (node.isDirectory()) {
            String[] subNote = node.list();
            for (String filename: subNote) {
                generateFileList(source, new File(node, filename));
            }
        }

        return fileList;
    }

    private static String generateZipEntry(String source, String file) {
        return file.substring(source.length() + 1, file.length());
    }
}