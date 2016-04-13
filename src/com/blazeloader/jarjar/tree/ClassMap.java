package com.blazeloader.jarjar.tree;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public class ClassMap extends HashMap<String, ClassNode> {
	private final int directives;
	
	public ClassMap() {
		this(ClassReader.SKIP_CODE);
	}
	
	public ClassMap(int directive) {
		directives = directive;
	}
	
	public void readFromJar(File jarFile) throws IOException {
		clear();
		JarFile jar = null;
		try {
			jar = new JarFile(jarFile, false);
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (name.endsWith(".class")) {
					name = name.split("\\.")[0];
					ClassReader reader = new ClassReader(jar.getInputStream(entry));
					ClassNode classNode = new ClassNode();
					reader.accept(classNode, directives);
					put(name, classNode);
				}
			}
			System.out.println(size() + " classes successfully loaded from jar");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (jar != null) jar.close();
		}
	}
	
	public Entry<String, ClassNode> getEntry(String key) {
		for (Entry<String, ClassNode> i : entrySet()) {
			if (i.getKey().equals(key)) return i;
		}
		return null;
	}
}
