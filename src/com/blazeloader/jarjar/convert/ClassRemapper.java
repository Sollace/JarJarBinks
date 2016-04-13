package com.blazeloader.jarjar.convert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.blazeloader.jarjar.tree.ClassMap;
import com.blazeloader.jarjar.tree.ClassTree;
import com.blazeloader.jarjar.tree.ClassTreeMatcher;

import net.acomputerdog.OBFUtil.map.TargetType;
import net.acomputerdog.OBFUtil.table.DirectOBFTable;
import net.acomputerdog.OBFUtil.util.Obfuscator;

public class ClassRemapper {
	private Obfuscator obfuscator = new Obfuscator();
	private ClassMap source = new ClassMap(0);
	private ClassMap destination = new ClassMap(0);
	
	private ClassTree from;
	private ClassTree to;
	
	private ClassTreeMatcher matcher = null;
	
	public ClassRemapper(File sourceJar, File destinationJar) {
		try {
			source.readFromJar(sourceJar);
			destination.readFromJar(destinationJar);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void buildClassTrees() {
		if (from == null) {
			System.out.println("Building source class tree");
			from = new ClassTree(source);
			System.out.println("Source class tree has " + from.size() + " members.");
			System.out.println("Sorting source tree...");
			from.sort();
		}
		if (to == null) {
			System.out.println("Building destination class tree");
			to = new ClassTree(destination);
			System.out.println("Destination class tree has " + to.size() + " members.");
			System.out.println("Sorting destination tree...");
			to.sort();
		}
	}
	
	public ClassTreeMatcher loadMatcher() {
		buildClassTrees();
		System.out.println("Performing best match analysis");
		matcher = new ClassTreeMatcher(from, to);
		System.out.println("Classes matched: " + matcher.size());
		System.out.println("Average match: " + Math.floor(matcher.averageSimilarity() * 100)/100 + "%");
		return matcher;
	}
	
	public ClassTreeMatcher loadMatcherFromJson(String path) {
		System.out.println("Loading mapping tree from: " + path);
		buildClassTrees();
		File input = new File(path);
		if (input.exists()) {
			String json = "";
			String line;
			BufferedReader buff = null;
			try {
				buff = new BufferedReader(new FileReader(input));
				while ((line = buff.readLine()) != null) json += line;
			} catch (IOException e) {
				e.printStackTrace();
				return matcher = null;
			} finally {
				if (buff != null) {
					try {
						buff.close();
					} catch (IOException e) {}
				}
			}
			matcher = ClassTreeMatcher.loadFromJson(from, to, json);
			System.out.println("Match tree successfuly loaded");
			System.out.println("Classes matched: " + matcher.size());
			System.out.println("Average match: " + Math.floor(matcher.averageSimilarity() * 100)/100 + "%");
			return matcher;
		}
		System.out.println("Error: File not found.");
		return null;
	}
	
	private ClassTreeMatcher resolveAnonymouse(ClassTreeMatcher matcher, String before) {
		ClassTreeMatcher after = matcher.lookup(before);
		if (after == null && ClassTree.isAnon(before)) {
			return resolveAnonymouse(matcher, ClassTree.getOuter(before));
		}
		return after;
	}
	
	public DirectOBFTable remapTree(DirectOBFTable table) {
		DirectOBFTable result = new DirectOBFTable();
		String[] classes = table.getAllObf(TargetType.CLASS);
		
		if (matcher == null) loadMatcher();
		
		System.out.println("Remapping obfuscation classes...");
		int skippedClasses = 0;
		for (String className : classes) {
			String before = className.replace('.', '/');
			ClassTreeMatcher after = resolveAnonymouse(matcher, before);
			if (after != null) {
				before = before.replace(after.before().getName(), "");
				String obf = after.after().getName() + before;
				if (result.hasObf(obf, TargetType.CLASS)) {
					System.err.println("Error: Duplicate class obfuscation: [" + before + "] " + obf + " -> " + className);
					System.err.println("       Already mapped as: [" + before + "] " + obf + " -> " + result.deobf(obf, TargetType.CLASS));
				}
				result.addType(obf, table.deobf(className, TargetType.CLASS), TargetType.CLASS);
			} else {
				System.out.println("Error: Classname missing from trees: \"" + className + "\" was \"" + table.deobf(className, TargetType.CLASS) + "\" Skipping.");
				//result.addType(className, table.deobf(className, TargetType.CLASS), TargetType.CLASS);
				skippedClasses++;
			}
		}
		if (skippedClasses > 0) System.err.println("Error: " + skippedClasses + " classes skipped.");
		classes = result.getAllObf(TargetType.CLASS);
		for (String field : table.getAllDeobf(TargetType.FIELD)) {
			String obfName = obfuscator.getMemberName(table.obf(field, TargetType.FIELD));
			String mcpClass = obfuscator.getMemberClass(field);
			if (!result.hasDeobf(mcpClass, TargetType.CLASS)) continue;
			String obfClass = result.obf(mcpClass, TargetType.CLASS);
			result.addType(obfClass + "." + obfName, field, TargetType.FIELD);
		}
		for (String method : table.getAllDeobf(TargetType.METHOD)) {
			String[] split = method.split(" ");
			String obfName = obfuscator.getMemberName(table.obf(method, TargetType.METHOD));
			String mcpClass = obfuscator.getMemberClass(split[0]);
			if (!result.hasDeobf(mcpClass, TargetType.CLASS)) continue;
			String obfClass = result.obf(mcpClass, TargetType.CLASS);
			String obfDescriptor = null;
			/*ClassNode dest = destination.get(obfClass);
			if (dest != null) {
				for (MethodNode meth : (List<MethodNode>)dest.methods) {
					if (meth.name.equals(obfName)) {
						obfDescriptor = meth.desc;
						break;
					}
				}
			}*/
			if (obfDescriptor == null) {
				obfDescriptor = obfuscator.obfuscateDescriptor(method.split(" ")[1], result);
			}
			String mcpDescriptor = obfuscator.deObfuscateDescriptor(obfDescriptor, result);
			result.addType(obfClass + "." + obfName + " " + obfDescriptor, split[0] + " " + mcpDescriptor, TargetType.METHOD);
		}
		for (String pack : table.getAllDeobf(TargetType.PACKAGE)) {
			result.addType(table.obf(pack, TargetType.PACKAGE), pack, TargetType.PACKAGE);
		}
		return result;
	}
}
