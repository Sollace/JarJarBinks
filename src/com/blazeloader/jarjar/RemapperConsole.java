package com.blazeloader.jarjar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import com.blazeloader.jarjar.console.CommandReader;
import com.blazeloader.jarjar.console.Options;
import com.blazeloader.jarjar.convert.ClassRemapper;
import com.blazeloader.jarjar.convert.SeargToEnigmaConverter;
import com.blazeloader.jarjar.tree.ClassTree;
import com.blazeloader.jarjar.tree.ClassTreeMatcher;

import net.acomputerdog.OBFUtil.map.TargetType;
import net.acomputerdog.OBFUtil.parse.types.SRGFileParser;
import net.acomputerdog.OBFUtil.table.DirectOBFTable;
import net.acomputerdog.OBFUtil.table.OBFTable;

public class RemapperConsole extends CommandReader {
	private ClassTreeMatcher matcher;
	
	private final ClassRemapper remapper;
	private final File srg;
	
	private DirectOBFTable srgtable;
	private SRGFileParser parser;
	
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
					out.print("{null} <- ");
					if (full) return unmapped.toString();
					return unmapped.description();
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
					out.print("{null} -> ");
					if (full) return unmapped.toString();
					return unmapped.description();
				}
				return "Entry not found";
			}
			case "lookup-parent": {
				if (args.length < 2) return "Error: Too few arguments";
				boolean reverse = args.length == 3 && "reverse".equalsIgnoreCase(args[2]);
				ClassTree tree = reverse ? matcher.after() : matcher.before();
				ClassTreeMatcher result = reverse ? matcher.reverseLookup(args[1]) : matcher.lookup(args[1]);
				if (result != null) return result.parent().description();
				tree = tree.lookup(args[1]);
				if (tree != null) {
					tree = tree.parent();
					result = reverse ? matcher.reverseLookup(tree.getName()) : matcher.lookup(tree.getName());
					if (result != null) return result.description();
					out.print(reverse ? "{null} <- " : "{null} -> ");
					return tree.description();
				}
				return "Entry not found.";
			}
			case "lookup-obf": {
				if (args.length < 2) return "Error: Too few arguments";
				OBFTable table = loadSrg();
				if (!table.hasDeobf(args[1], TargetType.CLASS)) {
					args[1] = args[1].replace('/', '.');
				}
				if (table.hasDeobf(args[1], TargetType.CLASS)) {
					String result = table.obf(args[1], TargetType.CLASS);
					ClassTreeMatcher match = matcher.lookup(result);
					if (match != null) {
						return match.bitMask() + " " + match.description();
					}
				}
				return "Entry not found.";
			}
			case "lookup-mcp": {
				if (args.length < 2) return "Error: Too few arguments";
				boolean reverse = args.length == 3 && "reverse".equalsIgnoreCase(args[2]);
				ClassTreeMatcher found = reverse ? matcher.reverseLookup(args[1]) : matcher.lookup(args[1]);
				if (found != null) {
					OBFTable table = loadSrg();
					if (table.hasObf(found.before().getName(), TargetType.CLASS)) {
						return table.deobf(found.before().getName(), TargetType.CLASS);
					}
				}
				return "Entry not found";
			}
			case "define": {
				if (args.length < 3) return "Error: Too few arguments";
				ClassTree from = matcher.before().lookup(args[1]);
				ClassTree to = matcher.after().lookup(args[2]);
				if (from == null) return "Error: From class not found.";
				if (to == null) return "Error: To class not found.";
				matcher.define(from, to);
				ClassTreeMatcher result = matcher.lookup(from.getName());
				return result.bitMask() + " " + result.description();
			}
			case "swap": {
				if (args.length < 3) return "Error: Too few arguments";
				ClassTreeMatcher old = matcher.lookup(args[1]);
				ClassTreeMatcher neu = matcher.lookup(args[2]);
				if (old == null) return "Error: class not found: " + args[1];
				if (neu == null) return "Error: class not found: " + args[2];
				old.swap(neu);
				out.println(old.bitMask() + " " + old.description());
				return neu.bitMask() + " " + neu.description();
			}
			case "save-mcp": {
				if (args.length < 2) return "Error: Too few arguments.";
				args[1] = fillSystemVariables(args[1]);
				File output = new File(args[1]);
				OBFTable table = remapper.remapTree(loadSrg());
				parser.storeEntries(output, table);
				out.println("Conversion complete.");
				return "MCP mappings saved to " + args[1];
			}
			case "save-enigma": {
				if (args.length < 2) return "Error: Too few arguments.";
				args[1] = fillSystemVariables(args[1]);
				File output = new File(args[1]);
				OBFTable table = remapper.remapTree(loadSrg());
				SeargToEnigmaConverter converter = new SeargToEnigmaConverter(remapper.destination(), table);
				converter.storeEntries(output);
				return "Enigma mappings saved to " + args[1];
			}
			case "save-tree": {
				if (args.length < 2) return "Error: Too few arguments.";
				args[1] = fillSystemVariables(args[1]);
				File output = new File(args[1]);
				Writer w = new BufferedWriter(new FileWriter(output));
				w.write(matcher.toString());
				w.close();
				return "Tree text saved to " + args[1];
			}
			case "save-json": {
				if (args.length < 2) return "Error: Too few arguments.";
				args[1] = fillSystemVariables(args[1]);
				File output = new File(args[1]);
				Writer w = new BufferedWriter(new FileWriter(output));
				w.write(matcher.writeToJson());
				w.close();
				return "Tree json saved to " + args[1];
			}
			case "load-json": {
				if (args.length < 2) return "Error: Too few arguments.";
				args[1] = fillSystemVariables(args[1]);
				matcher = remapper.loadMatcherFromJson(args[1]);
				if (matcher == null){
					return "Error: Could not load matcher.";
				}
				return "Table loaded.";
			}
			case "list": {
				if (args.length < 2) return "Error: Too few arguments.";
				int count = 0;
				switch (args[1].toLowerCase()) {
					case "unmatched": {
						out.println("Unmatched source classes:");
						for (String i : matcher.getUnmatched()) {
							ClassTree item = matcher.before().lookup(i);
							out.println(++count + ". Parent: " + item.parent().description());
							out.println(item.toString());
						}
						break;
					}
					case "unassigned": {
						out.println("Unassigned destination classes:");
						for (String i : matcher.getUnassigned()) {
							ClassTree item = matcher.after().lookup(i);
							out.println(++count + ". Parent: " + item.parent().description());
							out.println(item.toString());
						}
					}
				}
				return null;
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
					return "Terminate with \"\\\"";
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
						out.print(reverse ? "{null} -> " : "{null} <- ");
						out.println(node.description());
					} else {
						if (ops.containsKey("samefields") && ops.getBool("samefields") != (looked.before().fields() == looked.after().fields())) continue;
						if (ops.containsKey("samemethods") && ops.getBool("samemethods") != (looked.before().methods() == looked.after().methods())) continue;
						if (ops.containsKey("sameinterfaces") && ops.getBool("sameinterfaces") != (looked.before().interfaces() == looked.after().interfaces())) continue;
						out.println(looked.description());
					}
					matches++;
				}
				return matches + " classes matched.";
			}
		}
		return "Command Not Found: " + args[0];
	}
	
	private OBFTable loadSrg() throws IOException {
		if (srgtable == null) {
			parser = new SRGFileParser("", false);
			srgtable = new DirectOBFTable();
			parser.loadEntries(srg, srgtable, true);
		}
		return srgtable;
	}
	
	public void printHelp() {
		out.println("lookup [classname] {full}\t-\tRetrieves a single class mapping from a loaded tree.");
		out.println("lookup-reverse [classname] {full}\t-\tRetrieves the class that is mapped into the given one.");
		out.println("lookup-parent [classname] {reverse}\t-\tRetrieves the parent of the given class.");
		out.println("lookup-obf [mcp name]\t-\tFinds the obfuscated name for the given MCP name.");
		out.println("lookup-mcp [obf name]\t-\tFinds the MCP name for the given obfuscated name.");
		out.println("define [from] [to]\t-\tDefines a new mapping between two classes. (parents/children/interfaces are implicitly defined)");
		out.println("swap [one] [two]\t-\tSwaps the destinations of two classes.");
		out.println("save-mcp [file]\t-\tRemap and output mcp mappings for the current class map tree.");
		out.println("save-enigma [file]\t-\tCreates enigma mappings from the current tree that applies to the destination jar");
		out.println("save-tree [file]\t-\tOutput the entire class map tree to file.");
		out.println("save-json [file]\t-\tSaves the match tree to json. Useful if you want to edit it later.");
		out.println("load-json [file]\t-\tLoads a match tree from json.");
		out.println("list [unmatched|unassigned]\t-\tPrints a list of all classes not included in the matching tree.");
		out.println("grep {reverse}\t-\tFinds classes matching given conditions.");
		out.println("exit\t-\tExit");
	}
}
