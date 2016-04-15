package com.blazeloader.jarjar.convert;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import com.blazeloader.jarjar.tree.ClassMap;

import net.acomputerdog.OBFUtil.map.TargetType;
import net.acomputerdog.OBFUtil.parse.types.SRGFileParser;
import net.acomputerdog.OBFUtil.table.DirectOBFTable;
import net.acomputerdog.OBFUtil.table.OBFTable;
import net.acomputerdog.OBFUtil.util.Obfuscator;
import net.acomputerdog.core.java.Patterns;

public class SeargToEnigmaConverter {
	private final Obfuscator obfuscator = new Obfuscator();
	
	private ClassMap classes;
	private OBFTable table;
	
	public SeargToEnigmaConverter(boolean side, File seargeFile, File jarFile) {
		SRGFileParser seargeParser = new SRGFileParser(side ? "C"  : "S", false);
		classes = new ClassMap();
		table = new DirectOBFTable();
		try {
			classes.readFromJar(jarFile);
			seargeParser.loadEntries(seargeFile, table, true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public SeargToEnigmaConverter(ClassMap classes, OBFTable table) {
		this.classes = classes;
		this.table = table;
	}
	
	public String fieldType(String className, String memberName) {
		if (classes.containsKey(className)) {
			ClassNode node = classes.get(className);
			for (FieldNode i : (List<FieldNode>)(node.fields)) {
				if (i.name.equals(memberName)) {
					return i.desc;
				}
			}
			return null;
		}
		return null;
	}
	
	public void storeEntries(File file) throws IOException {
		if (file == null) {
            throw new IllegalArgumentException("File must not be null!");
        }
        Writer out = null;
        try {
            out = new BufferedWriter(new FileWriter(file));
            System.out.println("Strip generics: Because enigma doesn't support those. -_-;");
            String[] classes = table.getAllObf(TargetType.CLASS);
            String[] methods = table.getAllObf(TargetType.METHOD);
            String[] fields = table.getAllObf(TargetType.FIELD);
            for (String i : classes) {
            	if (i.indexOf("$") != -1) continue;
            	writeClass(out, "", i, classes, methods, fields);
            }
		} finally {
            if (out != null) {
                out.close();
            }
        }
	}
	
	private String stripGenerics(String in) {
		return in.replaceAll("([^<]*)[^>]*>*([^>]*)", "$1$2");
	}
	
	private void writeClass(Writer out, String indent, String className, String[] classes, String[] methods, String[] fields) throws IOException {
		String deobfClass = table.deobf(className, TargetType.CLASS);
		int dollar = deobfClass.lastIndexOf("$");
		String dumpedClass = (dollar >= 0 ? deobfClass.substring(dollar+1, deobfClass.length()) : deobfClass);
		dollar = dumpedClass.lastIndexOf("\\.");
		if (dollar != -1) {
			dumpedClass = dumpedClass.substring(dollar+1, deobfClass.length());
		}
		
    	out.write(indent + "CLASS ");
    	String obfDumpedClassName = className.replace('.', '/');
    	if (obfDumpedClassName.indexOf('/') == -1) out.write("none/");
		out.write(obfDumpedClassName);
		if (!Character.isDigit(dumpedClass.charAt(0))) { //Don't write deobfuscated class names for anonymous classes
			if (dumpedClass.indexOf('.') == -1) {
				if (indent.isEmpty()) dumpedClass = "none/" + dumpedClass;
			} else {
				dumpedClass = dumpedClass.replace('.', '/');
			}
			out.write(" ".concat(dumpedClass));
		}
		out.write(Patterns.LINE_SEPARATOR);
		String prefix = className + ".";
		for (String clazz: classes) {
			if (clazz.indexOf(className + "$") == 0) {
				writeClass(out, indent + "\t", clazz, classes, methods, fields);
			}
		}
    	for (String field : fields) {
    		if (field.indexOf(prefix) == 0) {
    			String name = field.replace(prefix, "");
    			String type = fieldType(obfDumpedClassName, name);
    			String deobf = table.deobf(field, TargetType.FIELD).replace(deobfClass + ".", "");
    			if (name.indexOf('$') != -1 || deobf.indexOf('$') != -1) continue; //Skip synthetic fields
    			if (type != null) {
        			if (deobf.indexOf("field_") == 0) continue; //Skip searge-named fields
        			out.write(indent + "\tFIELD " + name + " " + deobf + " " + insertNullPackage(stripGenerics(type)) + Patterns.LINE_SEPARATOR);
    			} else {
    				System.out.println("Error: No signature information for field " + deobf + " in class " + deobfClass);
    			}
    		}
    	}
    	for (String method : methods) {
    		if (method.indexOf(prefix) == 0) {
        		String[] name = method.replace(prefix, "").split(" ");
        		String deobf = table.deobf(method, TargetType.METHOD).split(" ")[0].replace(deobfClass + ".", "");
        		if (name[0].indexOf('$') != -1 || deobf.indexOf('$') != -1) continue; //Skip synthetic methods
        		if (deobf.indexOf("func_") == 0) continue; //Skip searge-named methods
        		out.write(indent + "\tMETHOD " + name[0] + " " + deobf + " " + insertNullPackages(stripGenerics(name[1])) + Patterns.LINE_SEPARATOR);
    		}
    	}
	}
	
	private String insertNullPackages(String descriptor) {
		String obfuscatedDescriptor = "";
		String[] split = descriptor.split("\\)");
		List<String> paramaters = Obfuscator.splitDescriptor(split[0]);
		if (split.length < 2) throw new IllegalArgumentException("Missing return type for \"" + descriptor + "\"");
		for (String i : paramaters) {
			obfuscatedDescriptor += insertNullPackage(i);
		}
    	return "(" + obfuscatedDescriptor + ")" + insertNullPackage(split[1]);
	}
	
	private String insertNullPackage(String type) {
		if (type.endsWith(";") && type.indexOf('/') == -1) {
			String clazz = obfuscator.extractClass(type);
			
			return type.replace(clazz, "none/" + clazz);
		}
		return type;
	}
}
