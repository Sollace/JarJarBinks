package com.blazeloader.jarjar.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map.Entry;
import java.util.Set;

public abstract class CommandReader implements Runnable {
	private static final String spliterator = " (?=([^\"]*\"[^\"]*\")*[^\"]*$)";
	
	protected final InputStream in;
	
	protected final PrintStream out;
	
	private BufferedReader reader;
	
	public CommandReader(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out instanceof PrintStream ? (PrintStream)out : new PrintStream(out);
	}
	
	public void run() {
		out.println("Command Reader Console 1.0");
		out.println("Type \"help\" to see a list of available commands.");
		reader = new BufferedReader(new InputStreamReader(in));
		String input = null;
		try {
			while (!"exit".equals(input = reader.readLine())) {
				if (input != null && !input.trim().isEmpty()) {
					String[] split = input.split(spliterator, -1);
					for (int i = 0; i < split.length; i++) {
						if (split[i].startsWith("\"")) split[i] = split[i].substring(1);
						if (split[i].endsWith("\"")) split[i] = split[i].substring(0, split[i].length()-1);
					}
					if (split.length > 0) {
						if (split[0].toLowerCase().equals("help")) {
							printHelp();
						} else {
							try {
								String output = handleCommand(split);
								if (output != null && !output.isEmpty()) {
									out.println(output);
								}
							} catch (Throwable e) {
								this.out.print("Unknown error: ");
								e.printStackTrace(this.out);
							}
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			
		}
	}
	
	protected String fillSystemVariables(String line) {
		if (line.indexOf('%') == -1 && line.indexOf('$') == -1) return line;
		Set<Entry<String, String>> keys = System.getenv().entrySet();
		if (line.indexOf('%') != -1) {
			for (Entry<String, String> e : keys) {
				line = line.replaceAll("(?i)%" + e.getKey() + "%", e.getValue().replace('\\', '/'));
			}
		}
		if (line.indexOf('$') != -1) {
			String[] components = line.split("\\$");
			for (Entry<String, String> e : keys) {
				String s = e.getKey();
				String v = e.getValue().replace('\\', '/');
				for (int i = 0; i < components.length; i++) {
					if (components[i].startsWith(s)) {
						components[i] = components[i].replaceFirst(s, v);
					}
				}
			}
			StringBuilder builder = new StringBuilder();
			for (String i : components) builder.append(i);
			line = builder.toString();
		}
		return line;
	}
	
	protected String readLine() throws IOException {
		return reader.readLine();
	}
	
	public abstract String handleCommand(String... args) throws Throwable;
	
	public abstract void printHelp();
}
