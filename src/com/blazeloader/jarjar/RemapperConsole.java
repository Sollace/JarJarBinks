package com.blazeloader.jarjar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import com.blazeloader.jarjar.console.CommandReader;
import com.blazeloader.jarjar.console.Options;
import com.blazeloader.jarjar.convert.ClassRemapper;
import com.blazeloader.jarjar.tree.ClassTree;
import com.blazeloader.jarjar.tree.ClassTreeMatcher;

import net.acomputerdog.OBFUtil.parse.types.SRGFileParser;
import net.acomputerdog.OBFUtil.table.DirectOBFTable;

public class RemapperConsole extends CommandReader {
	private ClassTreeMatcher matcher;
	
	private final ClassRemapper remapper;
	private final File srg;
	
	public RemapperConsole(ClassRemapper remapper, File srg) {
		this(remapper, srg, null);
	}
	
	public RemapperConsole(ClassRemapper remapper, File srg, String json) {
		super(System.in, System.out);
		this.remapper = remapper;
		this.srg = srg;
		if (json != null) {
			matcher = remapper.loadMatcherFromJson(json);
			if (matcher == null){
				System.out.println("Error: Could not load matcher.");
			}
		} else {
			matcher = remapper.loadMatcher();
		}
	}
	
	public String handleCommand(String... args) throws Throwable {
		switch (args[0].toLowerCase()) {
			case "lookup": {
				if (args.length < 2) return "Error: Too few arguments";
				ClassTreeMatcher found = matcher.lookup(args[1]);
				boolean full = args.length > 2 && "full".equalsIgnoreCase(args[2]);
				if (found != null) {
					if (full) return found.toString();
					return found.bitMask() + " " + found.description();
				}
				ClassTree unmapped = matcher.before().lookup(args[1]);
				if (unmapped != null) {
					out.println("Class found but not matched: ");
					return unmapped.toString();
				}
				return "Entry not found";
			}
			case "lookup-reverse": {
				if (args.length < 2) return "Error: Too few arguments";
				ClassTreeMatcher found = matcher.reverseLookup(args[1]);
				boolean full = args.length > 2 && "full".equalsIgnoreCase(args[2]);
				if (found != null) {
					if (full) return found.toString();
					return found.bitMask() + " " + found.description();
				}
				ClassTree unmapped = matcher.after().lookup(args[1]);
				if (unmapped != null) {
					out.println("Class found but not matched: ");
					return unmapped.toString();
				}
				return "Entry not found";
			}
			case "lookup-parent": {
				if (args.length < 2) return "Error: Too few arguments";
				boolean reverse = args.length == 3 && "reverse".equalsIgnoreCase(args[2]);
				ClassTreeMatcher result = null;
				if (!reverse) {
					result = matcher.lookup(args[1]);
					if (result != null) {
						return result.parent().description();
					}
					ClassTree parent = matcher.before().lookup(args[1]);
					if (parent != null) return parent.parent().getName();
				}
				result = matcher.reverseLookup(args[1]);
				if (result != null) {
					return result.parent().description();
				}
				ClassTree parent = matcher.after().lookup(args[1]);
				if (parent != null) return parent.parent().getName();
				return "Entry not found.";
			}
			case "define": {
				if (args.length < 3) return "Error: Too few arguments";
				ClassTree from = matcher.before().lookup(args[1]);
				ClassTree to = matcher.after().lookup(args[2]);
				if (from == null) return "Error: From class not found.";
				if (to == null) return "Error: To class not found.";
				matcher.define(from, to);
				return handleCommand("get", args[2]);
			}
			case "swap": {
				if (args.length < 3) return "Error: Too few arguments";
				ClassTreeMatcher old = matcher.lookup(args[1]);
				ClassTreeMatcher neu = matcher.lookup(args[2]);
				if (old == null) return "Error: class not found: " + args[1];
				if (neu == null) return "Error: class not found: " + args[2];
				old.swap(neu);
				out.println(handleCommand("get", args[1]));
				return handleCommand("get", args[2]);
			}
			case "save-mcp": {
				if (args.length < 2) return "Error: Too few arguments.";
				File output = new File(args[1]);
				SRGFileParser parser = new SRGFileParser("", false);
				DirectOBFTable table = new DirectOBFTable();
				parser.loadEntries(srg, table, true);
				table = remapper.remapTree(table);
				parser.storeEntries(output, table);
				out.println("Conversion complete.");
				return "result saved to " + args[1];
			}
			case "save-tree": {
				if (args.length < 2) return "Error: Too few arguments.";
				File output = new File(args[1]);
				Writer w = new BufferedWriter(new FileWriter(output));
				w.write(matcher.toString());
				w.close();
				return "Tree saved to " + args[1];
			}
			case "save-json": {
				if (args.length < 2) return "Error: Too few arguments.";
				File output = new File(args[1]);
				Writer w = new BufferedWriter(new FileWriter(output));
				w.write(matcher.writeToJson());
				w.close();
				return "Tree saved to " + args[1];
			}
			case "load-json": {
				if (args.length < 3) return "Error: Too few arguments.";
				matcher = remapper.loadMatcherFromJson(args[2]);
				if (matcher == null){
					return "Error: Could not load matcher.";
				}
			}
			case "list": {
				if (args.length < 2) return "Error: Too few arguments.";
				int count = 0;
				switch (args[1].toLowerCase()) {
					case "unmatched": {
						out.println("Unmatched source classes:");
						for (String i : matcher.getUnmatched()) {
							ClassTree item = matcher.before().lookup(i);
							out.println(++count + ". Parent: " + item.parent().getName());
							out.println(item.toString());
						}
						break;
					}
					case "unassigned": {
						out.println("Unassigned destination classes:");
						for (String i : matcher.getUnassigned()) {
							ClassTree item = matcher.after().lookup(i);
							out.println(++count + ". Parent: " + item.parent().getName());
							out.println(item.toString());
						}
					}
				}
				break;
			}
			case "grep": {
				boolean reverse = args.length == 2 && "reverse".equalsIgnoreCase(args[1]);
				Options ops = new Options();
				String line;
				while ((line = readLine()) != null && !"\\".equals(line)) {
					ops.readOption(line);
				}
				if (ops.size() == 0) {
					out.println("Error: No conditions given!");
					out.println("Specify conditions one per line as: {property}={value}");
					out.println("Terminate with \"\\\"");
					return null;
				}
				ClassTree tree = reverse ? matcher.after() : matcher.before();
				int matches = 0;
				for (String i : tree.keySet()) {
					ClassTree node = tree.lookup(i);
					if (ops.containsKey("fields") && ops.getInt("fields") != node.fields()) continue;
					if (ops.containsKey("methods") && ops.getInt("methods") != node.methods()) continue;
					if (ops.containsKey("interfaces") && ops.getInt("interfaces") != node.interfaces()) continue;
					if (ops.containsKey("isInner") && ops.getBool("IsInner") != node.isInner()) continue;
					if (ops.containsKey("isInterface") && ops.getBool("IsInterface") != node.isInner()) continue;
					if (ops.containsKey("parent") && !ops.get("parent").equals(node.parent().getName())) continue;
					ClassTreeMatcher looked = reverse ? matcher.reverseLookup(i) : matcher.lookup(i);
					if (ops.containsKey("unmatched")) {
						if (ops.getBool("unmatched")) {
							if (looked != null) continue;
						} else {
							if (looked == null) continue;
						}
					}
					if (ops.containsKey("namechange")) {
						if (ops.getBool("namechange")) {
							if (looked != null && looked.before().getName().equals(looked.after().getName())) continue;
						} else {
							if (looked == null || !looked.before().getName().equals(looked.after().getName())) continue;
						}
					}
					if (looked == null) {
						out.println(i + " (Not in match table)");
					} else {
						if (ops.containsKey("samefields") && ops.getBool("samefields") != (looked.before().fields() == looked.after().fields())) continue;
						if (ops.containsKey("samemethods") && ops.getBool("samemethods") != (looked.before().methods() == looked.after().methods())) continue;
						if (ops.containsKey("sameinterfaces") && ops.getBool("sameinterfaces") != (looked.before().interfaces() == looked.after().interfaces())) continue;
						out.println(looked.description());
					}
					matches++;
				}
				out.println(matches + " classes matched.");
			}
		}
		return null;
	}
	
	public void printHelp() {
		out.println("lookup [classname] {full}\t-\tRetrieves a single class mapping from a loaded tree.");
		out.println("lookup-reverse [classname] {full}\t-\tRetrieves the class that is mapped into the given one.");
		out.println("lookup-parent [classname] {reverse}\t-\tRetrieves the parent of the given class.");
		out.println("define [from] [to]\t-\tDefines a new mapping between two classes. (parents/children/interfaces are implicitly defined)");
		out.println("swap [one] [two]\t-\tSwaps the destinations of two classes.");
		out.println("save-mcp [file]\t-\tRemap and output mcp mappings for the current class map tree.");
		out.println("save-tree [file]\t-\tOutput the entire class map tree to file.");
		out.println("save-json [file]\t-\tSaves the match tree to json. Useful if you want to edit it later.");
		out.println("load-json [file]\t-\tLoads a match tree from json.");
		out.println("list [matched|unassigned]\t-\tPrints a list of all classes not included in the matching tree.");
		out.println("grep {reverse}\t-\tFinds classes matching given conditions.");
		out.println("exit\t-\tExit");
	}
}
