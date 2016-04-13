package com.blazeloader.jarjar;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import com.blazeloader.bl.obf.OBFLevel;
import com.blazeloader.jarjar.convert.ClassRemapper;
import com.blazeloader.jarjar.convert.SeargToEnigmaConverter;
import com.google.common.io.Files;


public class Main {
	public static OBFLevel CURRENT;
	public static String MAPPING_SOURCE;
	
	public static void main(String[] args) {
		if (args.length > 0 && args[0].equals("enigma")) {
			welcome();
			convertMappings(args);
			return;
		}
		if (args.length > 0 && args[0].equals("remap")) {
			welcome();
			remapMCP(args);
			return;
		}
		if (args.length > 1) {
			if ("checksum".equals(args[0])) {
				getChecksum(new File(args[1]));
				return;
			}
			MAPPING_SOURCE = args[0];
			welcome();
			convertFile(new File(args[1]), args.length > 2 ? OBFLevel.valueOf(args[2]) : OBFLevel.OBF, args.length > 3);
			return;
		}
		if (args.length > 0 && "obfs".equals(args[0])) {
			obfs();
			return;
		}
		usage();
	}
	
	private static void setOut() {
		File logFile = null;
		try {
			String path = URLDecoder.decode(Main.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF-8");
			logFile = new File((new File(path)).getParentFile(), "jarjar.log");
			if (!logFile.exists()) logFile.createNewFile();
			System.setOut(new PrintStream(new MirroredOutputStream(System.out, new FileOutputStream(logFile))));
		} catch (IOException e) {
			e.printStackTrace();
			if (logFile != null) System.out.println(logFile.getAbsolutePath());
		}
	}
	
	private static void welcome() {
		setOut();
		System.out.println("#################################################");
		System.out.println("      JarJarBinks Access Transformer 1.0         ");
		System.out.println("              cc BlazeLoader 2016                ");
		System.out.println("#################################################");
	}
	
	private static void usage() {
		System.out.println("Usage:");
		System.out.println("jarjar <transformations file> <target jar> <obf level {OBF|SRG|MCP}> <replace?>");
	}
	
	private static void obfs() {
		System.out.println("Allowed obfuscation states:");
		for (OBFLevel i : OBFLevel.values()) {
			System.out.println("\t" + i.name());
		}
	}
	
	private static void remapMCP(String[] args) {
		if (args.length < 4) {
			System.out.println("jarjar remap <srg file> <source jar file> <destination jar file> [mapper json file]");
			return;
		}
		File srg = new File(args[1]);
		if (!srg.exists()) {
			System.out.println("Error: srg file does not exist.");
			return;
		}
		File jar1 = new File(args[2]);
		if (!jar1.exists()) {
			System.out.println("Error: source jar file does not exist.");
			return;
		}
		File jar2 = new File(args[3]);
		if (!jar2.exists()) {
			System.out.println("Error: destination jar file does not exist.");
			return;
		}
		
		System.out.println("Remapping jar classes. This may take a while.");
		ClassRemapper remapper = new ClassRemapper(jar1, jar2);
		RemapperConsole console;
		if (args.length == 5) {
			console = new RemapperConsole(remapper, srg, args[4]);
		} else {
			console = new RemapperConsole(remapper, srg);
		}
		console.run();
	}
	
	private static void convertMappings(String[] args) {
		if (args.length < 4) {
			System.out.println("jarjar enigma <srg file> <jar file> <output file>");
			return;
		}
		try {
			File srg = new File(args[1]);
			if (!srg.exists()) {
				System.out.println("Error: srg file does not exist.");
				return;
			}
			File jar = new File(args[2]);
			if (!jar.exists()) {
				System.out.println("Error: jar file does not exist.");
				return;
			}
			File out = new File(args[3] + ".enigma");
			System.out.println("Converting MCP mappings to enigma. Please wait.");
			(new SeargToEnigmaConverter(args.length > 4, srg, jar)).storeEntries(out);
			System.out.println("Conversion complete.");
			System.out.print("Result saved to: ");
			System.out.println(args[3] + ".enigma");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void convertFile(File in, OBFLevel level, boolean replace) {
		CURRENT = level;
		try {
			File resolved = resolveOld(in);
			System.out.println("Resolved Input: " + resolved.getAbsolutePath());
			if (!resolved.exists()) {
				System.out.println("Error: Input file does not exist.");
				return;
			}
			System.out.println("Transforming file. Please wait.");
			JarFile jar = new JarFile(resolved, false);
			File out = new File(jar.getName() + ".tmp");
			if (out.exists()) out.delete();
			out.createNewFile();
			MessageDigest sha = MessageDigest.getInstance("SHA-512");
			JarOutputStream jOut = null;
			try {
				jOut = new JarOutputStream(new DigestOutputStream(new FileOutputStream(out), sha));
				jOut.setComment(jar.getComment());
				new AccessTransformer().parse(jOut, jar);
			} finally {
				if (jar != null) jar.close();
				if (jOut != null) jOut.close();
			}
			if (replace) {
				if (in.exists()) displaceOldFiles(in);
				Files.move(out, in);
				dumpMD5File(sha, in);
			} else {
				dumpMD5File(sha, out);
			}
			System.out.println("Conversion complete");
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
	}
	
	private static File resolveOld(File in) {
		File old = new File(in.getParentFile(), in.getName() + ".old");
		if (old.exists()) return old;
		return in;
	}
	
	private static void displaceOldFiles(File in) throws IOException {
		File dir = in.getParentFile();
		File old = new File(dir, in.getName() + ".old");
		if (!old.exists()) {
			Files.move(in, old);
		} else {
			in.delete();
		}
		File md5File = new File(dir, in.getName() + ".md5");
		if (md5File.exists()) {
			old = new File(dir, md5File.getName() + ".old");
			if (!old.exists()) {
				Files.move(md5File, old);
			} else {
				md5File.delete();
			}
		}
	}
	
	private static void dumpMD5File(MessageDigest sha, File f) throws IOException {
		System.out.println("Output saved to: " + f.getAbsolutePath());
		BufferedWriter writer = null;
		try {
			System.out.println("Generating md5s...");
			f = new File(f.getParentFile(), f.getName() + ".md5");
			System.out.println("md5s saved to: " + f.getAbsolutePath()); 
			if (f.exists()) f.delete();
			f.createNewFile();
			writer = new BufferedWriter(new FileWriter(f));
			for (String i : chunk(String.format("%064x", new java.math.BigInteger(1, sha.digest()))).split("\n")) {
				writer.write(i);
				writer.newLine();
			}
			writer.flush();
			writer.close();
		} finally {
			if (writer != null) writer.close();
		}
	}
	
	private static void getChecksum(File file) {
		InputStream s = null;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
			MessageDigest sha1 = MessageDigest.getInstance("SHA");
			s = new DigestInputStream(new DigestInputStream(new DigestInputStream(new FileInputStream(file), md), sha512), sha1);
			while (s.read() != -1);
			System.out.println("MD5: \n" + formatMD5Checksum(md));
			System.out.println("SHA: \n" + chunk(String.format("%064x", new BigInteger(1, sha1.digest()))));
			System.out.println("SHA-512: \n" + chunk(String.format("%064x", new BigInteger(1, sha512.digest()))));
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (s != null) {
				try { s.close();
				} catch (IOException e) { }
			}
		}
	}
	
	private static String formatMD5Checksum(MessageDigest md) {
		   byte[] b = md.digest();
		   String result = "";
		   for (int i=0; i < b.length; i++) {
		       result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
		   }
		   return result;
	}
	
	private static String chunk(String s) {
		return s.replaceAll("(.{32})", "$1\n").trim();
	}
}