package br.inf.ufg.astorworker.utils;

import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Set;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;

public class FileSystemUtils {
	
	public static File createTemporaryCopyDirectory(File directory) throws Exception {
		File tmpDirectory = FileUtils.getTempDirectory();
		FileUtils.deleteDirectory(new File(tmpDirectory.getAbsolutePath() + File.separator + directory.getName()));
		FileUtils.copyDirectoryToDirectory(directory, tmpDirectory);
		return new File(tmpDirectory.getAbsolutePath() + File.separator + directory.getName());
	}

	private static void getAllPermissions(File file) throws Exception {
	    Set<PosixFilePermission> perms = Files.readAttributes(file.toPath(),PosixFileAttributes.class).permissions();
		perms.add(PosixFilePermission.OWNER_WRITE);
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_EXECUTE);
        perms.add(PosixFilePermission.GROUP_WRITE);
        perms.add(PosixFilePermission.GROUP_READ);
        perms.add(PosixFilePermission.GROUP_EXECUTE);
        perms.add(PosixFilePermission.OTHERS_WRITE);
        perms.add(PosixFilePermission.OTHERS_READ);
        perms.add(PosixFilePermission.OTHERS_EXECUTE);
        Files.setPosixFilePermissions(file.toPath(), perms);
	}

	public static void getPermissionsForDirectory(File directory) throws Exception {

		ArrayList<File> files = new ArrayList<>(Arrays.asList(directory.listFiles()));

    	for (File child : files){
    		if(child.isDirectory())
    			getPermissionsForDirectory(child);

    		getAllPermissions(child);
   	 	}

   	 	getAllPermissions(directory);
	}

	public static List<String> findFilesWithExtension(File directory, String extension) {

		ArrayList<String> filesFound = new ArrayList<>();
		ArrayList<File> files = new ArrayList<>(Arrays.asList(directory.listFiles()));

    	for (File child : files){
    		if(child.isDirectory())
    			findFilesWithExtension(child, extension);

    		if(child.getName().contains("." + extension))
    			filesFound.add(child.getName());
   	 	}

   	 	return filesFound;
	}
}