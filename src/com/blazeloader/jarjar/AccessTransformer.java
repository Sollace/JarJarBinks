package com.blazeloader.jarjar;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import com.blazeloader.util.transformers.ONFTransformer;
import com.blazeloader.util.transformers.transformations.Transformation;

import sun.misc.IOUtils;

public class AccessTransformer {
	private final ONFTransformer transformer = new ONFTransformer();
	
	public void parse(JarOutputStream jOut, JarFile jar) throws IOException  {
		Enumeration<JarEntry> entries = jar.entries();
		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			String name = entry.getName();
			InputStream s = null;
			try {
				s = jar.getInputStream(entry);
				if (name.endsWith(".class")) {
					String className = name.split("\\.")[0];
					byte[] data = transformer.transform(className, Transformation.getDotName(className), IOUtils.readFully(s, -1, true));
					jOut.putNextEntry(new JarEntry(entry.getName()));
					jOut.write(data);
					jOut.closeEntry();
				} else {
					jOut.putNextEntry(new JarEntry(entry.getName()));
					int b;
					while ((b = s.read()) != -1) {
						jOut.write(b);
					}
					jOut.closeEntry();
				}
			} finally {
				if (s != null) s.close();
			}
		}
	}
}
