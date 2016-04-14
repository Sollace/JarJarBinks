package com.blazeloader.jarjar.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import net.acomputerdog.core.java.Patterns;

public class ClassTreeMatcher implements Tree<ClassTreeMatcher, String> {
	private ClassTreeMatcher root;
	private ClassTreeMatcher parent;
	
	private ClassTree from;
	private ClassTree to;
	
	private double similarity;
	
	private List<ClassTreeMatcher> children = new ArrayList<ClassTreeMatcher>();
	private List<String> unmatchedClasses;
	private List<String> unassignedClasses;
	
	public static ClassTreeMatcher loadFromJson(ClassTree from, ClassTree to, String json) {
		JsonParser parser = new JsonParser();
		return new ClassTreeMatcher(from, to, parser.parse(json));
	}
	
	/**
	 * Creates a new class tree matcher with itself as the root.
	 * @param one	Class tree to map from
	 * @param two	Class tree to map to
	 */
	public ClassTreeMatcher(ClassTree one, ClassTree two) {
		this(100, one, two);
		root = this;
		initialise(null);
	}
	
	private ClassTreeMatcher(double sim, ClassTree one, ClassTree two) {
		similarity = sim;
		from = one;
		to = two;
	}
	
	private ClassTreeMatcher(ClassTree one, ClassTree two, JsonElement json) {
		this(100, one, two);
		root = this;
		loadChildrenFromJson(json);
	}
	
	private ClassTreeMatcher(ClassTreeMatcher root, JsonObject json) {
		this.root = root;
		if (json.has("from")) {
			from = root().from.lookup(json.get("from").getAsString());
		}
		if (json.has("to")) {
			to = root().to.lookup(json.get("to").getAsString());
		}
		loadChildrenFromJson(json);
	}
	
	private void loadChildrenFromJson(JsonElement json) {
		JsonObject object = json.getAsJsonObject();
		if (object.has("similarity")) similarity = object.get("similarity").getAsDouble();
		if (object.has("children")) {
			JsonArray arr = object.get("children").getAsJsonArray();
			for (int i = 0; i < arr.size(); i++) {
				addChild(new ClassTreeMatcher(root(), arr.get(i).getAsJsonObject()));
			}
		}
	}
	
	private ClassTreeMatcher initialise(ClassTreeMatcher root) {
		List<String> taken = new ArrayList<String>();
		taken.add(to.getName());
		List<String> matched = new ArrayList<String>();
		matched.add(from.getName());
		matchChilds(matched, taken);
		matchInnerClasses(matched, taken);
		int lostClasses = from.size() - matched.size();
		int available = from.size() - taken.size();
		if (lostClasses != 0) {
			System.err.println(lostClasses + " classes not identified.");
			System.err.println(available + " unmapped classes available.");
			matchLostClasses(matched, taken);
			matchLostInnerClasses(matched, taken);
			System.err.println((from.size() - matched.size()) + " classes not identified. (after retry)");
			System.err.println((from.size() - taken.size()) + " unmapped classes available. (after retry)");
		}
		if (root != null) this.root = root;
		root().recordUnmatchedClasses(this, matched, taken);
		return this;
	}
	
	public String writeToJson() {
		JsonObject element = writeChildsToJson(writeJson(new JsonObject()));
		if (unmatchedClasses != null) {
			JsonArray items = new JsonArray();
			for (String i : unmatchedClasses) {
				ClassTree node = from.lookup(i);
				if (node != null) items.add(new JsonPrimitive(i));
			}
			element.add("unmatched", items);
		}
		if (unassignedClasses != null) {
			JsonArray items = new JsonArray();
			for (String i : unassignedClasses) {
				ClassTree node = to.lookup(i);
				if (node != null) items.add(new JsonPrimitive(i));
			}
			element.add("unassigned", items);
		}
		return element.toString();
	}
	
	protected JsonObject writeJson(JsonObject element) {
		element.add("similarity", new JsonPrimitive(similarity));
		element.add("from", new JsonPrimitive(from.getName()));
		element.add("to", new JsonPrimitive(to.getName()));
		return element;
	}
	
	protected JsonObject writeChildsToJson(JsonObject element) {
		if (children.size() == 0) return element;
		JsonArray items = new JsonArray();
		for (ClassTreeMatcher i : children) {
			items.add(i.writeChildsToJson(i.writeJson(new JsonObject())));
		}
		element.add("children", items);
		return element;
	}
	
	private void recordUnmatchedClasses(ClassTreeMatcher sender, List<String> matched, List<String> taken) {
		if (unmatchedClasses == null) unmatchedClasses = new ArrayList<String>();
		if (unassignedClasses == null) unassignedClasses = new ArrayList<String>();
		List<String> froms = sender.from.keySet();
		froms.removeAll(matched);
		unmatchedClasses.removeAll(matched);
		for (String i : froms) {
			if (!unmatchedClasses.contains(i)) {
				unmatchedClasses.add(i);
			}
		}
		List<String> tos = sender.to.keySet();
		tos.removeAll(taken);
		unassignedClasses.removeAll(taken);
		for (String i : tos) {
			if (!unassignedClasses.contains(i)) {
				unassignedClasses.add(i);
			}
		}
	}
	
	private void matchLostClasses(List<String> matched, List<String> taken) {
		List<ClassTreeMatcher> lostChilds = new ArrayList<ClassTreeMatcher>();
		List<String> froms = from.keySet();
		froms.removeAll(matched);
		List<String> tos = to.keySet();
		tos.removeAll(taken);
		Iterator<String> iter = froms.iterator();
		while (froms.size() > 0) {
			int removals = 0;
			while (iter.hasNext()) {
				String i = iter.next();
				ClassTree f = from.lookup(i);
				if (f.isInner()) continue;
				ClassTreeMatcher parent = root().lookup(f.parent().getName());
				if (parent != null) {
					ClassTree lastTree = null;
					double lastSim = -1;
					for (String j : tos) {
						ClassTree t = to.lookup(j);
						if (t.isInner()) continue;
						double sim = f.similarity(t);
						if (sim >= lastSim) {
							lastSim = sim;
							lastTree = t;
						}
					}
					if (lastTree != null) {
						iter.remove();
						tos.remove(lastTree.getName());
						taken.add(lastTree.getName());
						matched.add(i);
						removals++;
						lostChilds.add(parent.addChild(new ClassTreeMatcher(lastSim, f, lastTree)));
					}
				}
			}
			if (removals == 0) break;
		}
	}
	
	private void matchLostInnerClasses(List<String> matched, List<String> taken) {
		List<ClassTreeMatcher> lostChilds = new ArrayList<ClassTreeMatcher>();
		List<String> froms = from.keySet();
		froms.removeAll(matched);
		List<String> tos = to.keySet();
		tos.removeAll(taken);
		Iterator<String> iter = froms.iterator();
		while (froms.size() > 0) {
			int removals = 0;
			while (iter.hasNext()) {
				String i = iter.next();
				ClassTree f = from.lookup(i);
				if (!f.isInner()) continue;
				ClassTreeMatcher parent = root().lookup(f.parent().getName());
				if (parent != null) {
					ClassTree lastTree = null;
					double lastSim = -1;
					for (String j : tos) {
						ClassTree t = to.lookup(j);
						if (!t.isInner()) continue;
						double sim = f.similarity(t);
						if (sim >= lastSim) {
							lastSim = sim;
							lastTree = t;
						}
					}
					if (lastTree != null) {
						iter.remove();
						tos.remove(lastTree.getName());
						taken.add(lastTree.getName());
						matched.add(i);
						removals++;
						lostChilds.add(parent.addChild(new ClassTreeMatcher(lastSim, f, lastTree)));
					}
				}
			}
			if (removals == 0) break;
		}
	}
	
	/**
	 * Recursively matches the nodes in this Matcher's two trees by similarity.
	 */
	private void matchChilds(List<String> matched, List<String> taken) {
		List<ClassTree> froms = new ArrayList<ClassTree>();
		froms.addAll(from.children());
		List<ClassTree> tos = to.children();
		Iterator<ClassTree> iter = froms.iterator();
		while (froms.size() > 0) {
			int removals = 0;
			while (iter.hasNext()) {
				ClassTree i = iter.next();
				if (i.isInner() || matched.contains(i.getName())) continue;
				if (i.getName().indexOf('/') != -1) {
					ClassTree other = to.lookup(i.getName());
					if (other != null) {
						matched.add(i.getName());
						taken.add(other.getName());
						addChild(new ClassTreeMatcher(100, i, other));
						iter.remove();
						removals ++;
						continue;
					}
				}
				
				ClassTree lastTree = null;
				double lastSim = -1;
				for (ClassTree j : tos) {
					double sim = i.similarity(j);
					if (sim > lastSim) {
						if (j.getName().indexOf('/') == -1) {
							lastTree = j;
							lastSim = sim;
						}
					}
				}
				if (lastTree != null) {
					removals ++;
					if (taken.contains(lastTree.getName())) {
						ClassTreeMatcher old = root().reverseLookup(lastTree.getName());
						if (lastSim > old.similarity) {
							matched.remove(old.from.getName());
							matched.add(lastTree.getName());
							froms.add(old.from);
							old.from = i;
							froms.remove(i);
							iter = froms.iterator();
						}
					} else {
						matched.add(i.getName());
						taken.add(lastTree.getName());
						iter.remove();
						addChild(new ClassTreeMatcher(lastSim, i, lastTree));
					}
				}
			}
			if (removals == 0) break;
		}
		for (ClassTreeMatcher i : children) {
			i.matchChilds(matched, taken);
		}
	}
	
	private void matchInnerClasses(List<String> matched, List<String> taken) {
		List<ClassTree> froms = new ArrayList<ClassTree>();
		froms.addAll(from.inner());
		List<ClassTree> tos = to.inner();
		Iterator<ClassTree> iter = froms.iterator();
		while (froms.size() > 0) {
			int removals = 0;
			while (iter.hasNext()) {
				ClassTree i = iter.next();
				if (!i.isInner() || matched.contains(i.getName())) continue;
				ClassTree lastTree = null;
				double lastSim = -1;
				for (ClassTree j : tos) {
					double sim = i.similarity(j);
					if (sim > lastSim) {
						if (j.getName().indexOf('/') == -1) {
							lastTree = j;
							lastSim = sim;
						}
					}
				}
				if (lastTree != null) {
					removals++;
					if (taken.contains(lastTree.getName())) {
						ClassTreeMatcher old = root().reverseLookup(lastTree.getName());
						if (lastSim > old.similarity) {
							matched.remove(old.from.getName());
							matched.add(lastTree.getName());
							froms.add(old.from);
							old.from = i;
							froms.remove(i);
							iter = froms.iterator();
						}
					} else {
						matched.add(i.getName());
						taken.add(lastTree.getName());
						iter.remove();
						addChild(new ClassTreeMatcher(lastSim, i, lastTree));
					}
				}
			}
			if (removals == 0) break;
		}
		for (ClassTreeMatcher i : children) {
			i.matchInnerClasses(matched, taken);
		}
	}
	
	/**
	 * Adds a mapping to the tree initialising parents and children if necessary.
	 */
	public ClassTreeMatcher define(ClassTree from, ClassTree to) {
		return define(from, to, true);
	}
	
	private ClassTreeMatcher define(ClassTree from, ClassTree to, boolean initialise) {
		ClassTreeMatcher parent = root().lookup(from.parent().getName());
		if (parent == null) {
			return define(from.parent(), to.parent(), false);
		}
		ClassTreeMatcher fromMatcher = root().lookup(from.getName());
		ClassTreeMatcher toMatcher = root().reverseLookup(to.getName());
		if (fromMatcher != null && toMatcher == null) {
			fromMatcher.to = to;
			fromMatcher.similarity = fromMatcher.from.similarity(to);
			return fromMatcher.parent();
		}
		if (toMatcher != null && fromMatcher == null) {
			toMatcher.from = from;
			toMatcher.similarity = from.similarity(toMatcher.to);
			return toMatcher.parent();
		}
		if (fromMatcher != null && toMatcher != null) {
			boolean parentOne = false;
			boolean parentTwo = false;
			parentOne = fromMatcher.parent().equals(parent);
			parentTwo = toMatcher.parent().equals(parent);
			if (parentOne && parentTwo) {
				fromMatcher.swap(toMatcher);
				return parent;
			}
			throw new IllegalStateException("Can only define a relationship between classes with the same parent");
		}
		ClassTreeMatcher child = parent.addChild(new ClassTreeMatcher(from.similarity(to), from, to));
		if (initialise) child.initialise(parent.root());
		getUnmatched().remove(from.getName());
		getUnassigned().remove(to.getName());
		return parent;
	}
	
	public ClassTreeMatcher addChild(ClassTreeMatcher child) {
		children.add(child);
		if (child.parent != null) {
			child.parent.children.remove(child);
		}
		child.parent = this;
		child.root = root();
		return child;
	}
	
	public ClassTreeMatcher sort() {
		Collections.sort(children);
		for (ClassTreeMatcher i : children) {
			i.sort();
		}
		return this;
	}
	
	/**
	 * Gets a list of all source classes not currently matched.
	 */
	public List<String> getUnmatched() {
		return unmatchedClasses == null && root() != this ? root().getUnmatched() : unmatchedClasses;
	}
	
	/**
	 * Gets a list of all destination classes not currently matched.
	 */
	public List<String> getUnassigned() {
		return unassignedClasses == null && root() != this ? root().getUnmatched() : unassignedClasses;
	}
	
	/**
	 * Gets the class this entry maps onto.
	 * @return
	 */
	public ClassTree after() {
		return to;
	}
	
	/**
	 * Gets the class this entry maps from.
	 * @return
	 */
	public ClassTree before() {
		return from;
	}
	
	public List<ClassTreeMatcher> children() {
		return children;
	}
	
	public ClassTreeMatcher parent() {
		return parent != null ? parent : root();
	}
	
	public ClassTreeMatcher root() {
		return root;
	}
	
	/**
	 * Looks up an entry by the name of it's source class.
	 */
	public ClassTreeMatcher lookup(String className) {
		if (from.getName().equals(className)) return this;
		ClassTreeMatcher result = null;
		for (ClassTreeMatcher i : children) {
			result = i.lookup(className);
			if (result != null) {
				return result;
			}
		}
		return result;
	}
	
	/**
	 * Looks up an entry by the name of it's destination class.
	 */
	public ClassTreeMatcher reverseLookup(String className) {
		if (to.getName().equals(className)) return this;
		ClassTreeMatcher result = null;
		for (ClassTreeMatcher i : children) {
			result = i.reverseLookup(className);
			if (result != null) return result;
		}
		return result;
	}
	
	public int size() {
		int size = 1;
		for (ClassTreeMatcher i : children) {
			size += i.size();
		}
		return size;
	}
	
	/**
	 * Swaps the destinations of this class and another and recomputes the similarity for these two elements.
	 */
	public void swap(ClassTreeMatcher other) {
		ClassTree inter = to;;
		to = other.to;
		other.to = inter;
		similarity = from.similarity(to);
		other.similarity = other.from.similarity(other.to);
	}
	
	public double similarity() {
		double sim = similarity;
		for (ClassTreeMatcher i : children) {
			sim += i.similarity();
		}
		return sim;
	}
	
	public double averageSimilarity() {
		return similarity() / size();
	}
	
	@Override
	public int compareTo(ClassTreeMatcher o) {
		//return from.compareTo(o.from);
		return Double.compare(o.similarity, similarity);
	}
	
	public String toString() {
		return toString("");
	}
	
	public String bitMask() {
		String result = "";
		
		result += (Math.floor(similarity * 100)/100) + "% ";
		
		boolean f = from.fields() == to.fields();
		boolean m = from.methods() == to.methods();
		boolean i = from.interfaces() == to.interfaces();
		result += f && m && i ? "1" : "0";
		
		boolean c = from.children().size() == to.children().size();
		boolean o = from.isInner() == to.isInner();
		
		result += c && o ? "1" : "0";
		
		result += " ";
		
		boolean a = from.isAnonymous() == to.isAnonymous();
		boolean an = from.isAbstract() == to.isAbstract();
		result += a && an ? "1" : "0";
		
		boolean n = from.getName().equals(to.getName());
		result += n ? "1" : "0";
		return result + " ";
	}
	
	public String description() {
		String result = from.getName() + " -> " + to.getName();
		return result + " { fields: " + from.fields() + " -> " + to.fields() + "; methods: " + from.methods() + " -> " + to.methods() + "; interfaces: " + from.interfaces() + " -> " + to.interfaces() + " }";
	}
	
	private String toString(String indent) {
		String result = "";
		result += bitMask();
		result += indent + description();
		result += Patterns.LINE_SEPARATOR;
		indent += "\t";
		for (ClassTreeMatcher i : children) {
			result += i.toString(indent);
		}
		return result;
	}
	
	public List<String> keySet() {
		List<String> result = new ArrayList<String>();
		buildKeySet(result);
		return result;
	}
	
	private void buildKeySet(List<String> keyset) {
		keyset.add(from.getName());
		for (ClassTreeMatcher i : children) {
			i.buildKeySet(keyset);
		}
	}
}
