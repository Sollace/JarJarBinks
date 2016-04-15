package com.blazeloader.jarjar.tree;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.collect.Lists;

import net.acomputerdog.core.java.Patterns;

public class ClassTree implements Tree<ClassTree, String> {
	
	private ClassTree parent = null;
	private ClassTree root = null;
	
	private final List<ClassTree> interfaces = new ArrayList<ClassTree>();
	private final List<ClassTree> inners = new ArrayList<ClassTree>();
	private final List<ClassTree> children = new ArrayList<ClassTree>();
	
	private final String className;
	private final String outer;
	
	private final ClassNode element;
	
	private final boolean anon;
	private boolean isinterface = false;
	private boolean isabstract = false;
	
	private final int fields;
	private final int methods;
	
	private int abstractMethods = 0;
	
	public ClassTree(ClassMap classMap) {
		element = null;
		className = "java/lang/Object";
		root = this;
		anon = false;
		outer = null;
		fields = methods = 0;
		List<Entry<String, ClassNode>> classes = Lists.newArrayList();
		classes.addAll(classMap.entrySet());
		loadChildren(classes);
		loadinterfaces(classes, classMap);
		loadOrphans(classes, classMap);
		cleanupAnonymouseClasses();
		initialiseInnerClasses();
	}
	
	private ClassTree(String name) {
		className = name;
		anon = isAnon(name);
		outer = getOuter(name);
		element = null;
		fields = methods = 0;
	}
	
	private ClassTree(String key, ClassNode element) {
		className = key;
		anon = isAnon(key);
		outer = getOuter(key);
		this.element = element;
		fields = element == null || element.fields == null ? 0 : element.fields.size();
		methods = element == null || element.methods == null ? 0 : element.methods.size();
		isabstract = (element.access & Opcodes.ACC_ABSTRACT) != 0;
		for (MethodNode i : (List<MethodNode>)element.methods) {
			if (Modifier.isAbstract(i.access)) abstractMethods++;
		}
	}
	
	public static boolean isAnon(String name) {
		int dollar = name.lastIndexOf('$');
		if (dollar == -1) return false;
		name = name.substring(dollar + 1, name.length());
		return Character.isDigit(name.charAt(0));
	}
	
	public static String getOuter(String name) {
		int dollar = name.lastIndexOf('$');
		if (dollar == -1) return null;
		name = name.substring(0, dollar);
		return name;
	}
	
	public static String getInner(String name) {
		int dollar = name.lastIndexOf('$');
		if (dollar == -1) return null;
		name = name.substring(dollar + 1, name.length());
		return name;
	}
	
	private ClassTree loadChildren(List<Entry<String, ClassNode>> classes) {
		Iterator<Entry<String, ClassNode>> iter = classes.iterator();
		while (iter.hasNext()) {
			Entry<String, ClassNode> entry = iter.next();
			if (classNameEquals(entry.getValue().superName)) {
				iter.remove();
				addChild(new ClassTree(entry.getKey(), entry.getValue()));
			}
		}
		for (ClassTree i : children) {
			i.loadChildren(classes);
		}
		return this;
	}
	
	/**
	 * Loads all interface classes and initialises unloaded children.
	 */
	private void loadinterfaces(List<Entry<String, ClassNode>> classes, ClassMap classMap) {
		if (element != null && element.interfaces != null) {
			for (String face : (List<String>)element.interfaces) {
				ClassTree iface = root().lookup(face);
				if (iface == null) {
					ClassNode node = classMap.get(face);
					if (node != null) {
						classes.remove(classMap.getEntry(face));
						iface = root().addChild(new ClassTree(face, node));
					}
				}
				if (iface != null) {
					interfaces.add(iface);
					iface.isinterface = true;
					iface.loadChildren(classes).loadinterfaces(classes, classMap);
				}
			}
		}
	}
	
	/**
	 * Loads classes that extend a class not included in the active set.
	 */
	private void loadOrphans(List<Entry<String, ClassNode>> classes, ClassMap classMap) {
		System.out.println("Loading orphaned classes...");
		int count = 0;
		Iterator<Entry<String, ClassNode>> iter = classes.iterator();
		while (iter.hasNext()) {
			Entry<String, ClassNode> entry = iter.next();
			iter.remove();
			ClassTree orphan = new ClassTree(entry.getKey(), entry.getValue());
			orphan.loadChildren(classes);
			iter = classes.iterator();
			ClassTree parent = root().lookup(orphan.element.superName);
			if (parent == null) {
				ClassNode node = classMap.get(orphan.element.superName);
				if (node != null) {
					classes.remove(classMap.getEntry(orphan.element.superName));
					iter = classes.iterator();
					parent = new ClassTree(orphan.element.superName, node);
				} else {
					parent = new ClassTree(orphan.element.superName);
				}
				root().addChild(parent);
			}
			parent.addChild(orphan);
			count++;
		}
		if (count > 0) System.out.println(count + " orphaned classes salvaged into {root}->{unknown/3rd party}->{orphan}");
	}
	
	/**
	 * Picks up classes that extends Object and implement only 1 interface.
	 */
	private void cleanupAnonymouseClasses() {
		List<ClassTree> anons = new ArrayList<ClassTree>();
		for (ClassTree i : children) {
			if (i.element == null) continue;
			List<String> ifaces = i.element.interfaces;
			if (ifaces != null && ifaces.size() == 1) anons.add(i);
		}
		int added = 0;
		int created = 0;
		for (ClassTree i : anons) {
			List<String> ifaces = i.element.interfaces;
			ClassTree face = root().lookup(ifaces.get(0));
			if (face == null) {
				created++;
				face = root().addChild(new ClassTree(ifaces.get(0)));
			}
			added++;
			face.addChild(i);
		}
		if (created > 0) System.out.println(created + " interfaces classes created at {root}->{interface/3rd party}");
		if (added > 0) System.out.println(added + " inner classes moved to {root}->{interface/3rd party}->{inner}");
	}
	
	private void initialiseInnerClasses() {
		for (ClassTree i : children) {
			if (i.isInner()) {
				i.outer().inners.add(i);
			}
			i.initialiseInnerClasses();
		}
	}
	
	public ClassTree sort() {
		Collections.sort(children);
		Collections.sort(inners);
		for (ClassTree i : children) {
			i.sort();
		}
		return this;
	}
	
	public ClassTree addChild(ClassTree child) {
		if (!child.isAnonymous()) {
			children.add(child);
			if (child.parent != null) {
				child.parent.children.remove(child);
			}
			child.parent = this;
			child.root = root();
		}
		return child;
	}
	
	public List<ClassTree> children() {
		return children;
	}
	
	/**
	 * Gets the number of interfaces implemented by this class.
	 */
	public int interfaces() {
		return interfaces.size();
	}
	
	/**
	 * Gets the number of fields on this class.
	 */
	public int fields() {
		return fields;
	}
	
	/**
	 * Gets the number of methods on this class.
	 */
	public int methods() {
		return methods;
	}
	
	/**
	 * Returns true if this is an anonymous class (has no name).
	 * Anonymous classes are inherently stripped from ClassTrees.
	 */
	public boolean isAnonymous() {
		return anon;
	}
	
	/**
	 * Returns true if this is an innerclass. i.e it has a outer counterpart.
	 */
	public boolean isInner() {
		return outer != null;
	}
	
	/**
	 * Returns true if this class has been used as an interface at least once.
	 */
	public boolean isInterface() {
		return isinterface;
	}
	
	/**
	 * Returns true if this class is abstract. (Interfaces are also abstract)
	 */
	public boolean isAbstract() {
		return isabstract || isInterface();
	}
	
	/**
	 * Gets the outer (containing) class of this one. Returns null for non-inner classes.
	 */
	public ClassTree outer() {
		return isInner() ? root().lookup(outer) : null;
	}
	
	/**
	 * Gets a list of all inner classes contained within this one.
	 */
	public List<ClassTree> inner() {
		return inners;
	}
	
	public ClassTree parent() {
		return parent != null ? parent : root();
	}
	
	public ClassTree root() {
		return root;
	}
	
	/**
	 * Gets this classes name.
	 */
	public String getName() {
		return className;
	}
	
	public ClassTree lookup(String className) {
		if (this.className.equals(className)) return this;
		ClassTree result = null;
		for (ClassTree i : children) {
			result = i.lookup(className);
			if (result != null) return result;
		}
		return result;
	}
	
	/**
	 * Calculates the similarity between two trees.
	 */
	public double similarity(ClassTree other) {
		return 100 * (similarityInt(other)/(Math.max(size() + implSize(), other.size() + other.implSize())));
	}
	
	/*private boolean compareMethodBodies(MethodNode one, MethodNode two) {
		int instructionMatches = 0;
		for (int i = 0; i < one.instructions.size() && i < two.instructions.size(); i++) {
			if (one.instructions.get(i).equals(two.instructions.get(i))) {
				instructionMatches ++;
			}
		}
		if (instructionMatches >= one.instructions.size() * 0.6f) return true;
		int instructionFrequencyMatches = 0;
		Map<Integer, Integer> instructionFrequenciesOne = getFrequencyMap(one);
		Map<Integer, Integer> instructionFrequenciesTwo = getFrequencyMap(two);
		for (Integer i : instructionFrequenciesOne.values()) {
			if (instructionFrequenciesTwo.containsKey(i)) {
				if (instructionFrequenciesTwo.get(i) == instructionFrequenciesOne.get(i)) {
					instructionFrequencyMatches ++;
				}
			}
		}
		if (instructionFrequencyMatches >= instructionFrequenciesOne.size() * 0.6f) return true;
		return false;
	}
	
	private Map<Integer, Integer> getFrequencyMap(MethodNode node) {
		Map<Integer, Integer> result = new HashMap<Integer, Integer>();
		for (int i = 0; i < node.instructions.size(); i++) {
			int op = node.instructions.get(i).getOpcode();
			if (result.containsKey(op)) {
				result.put(op, result.get(op) + 1);
			} else {
				result.put(op, 1);
			}
		}
		return result;
	}
	
	private double matchMethods(ClassTree other) {
		if (element == null || other.element == null) return 0;
		int total = Math.max(methods(), other.methods());
		if (total == 0) return 0;
		float matched = 0;
		for (MethodNode m : (List<MethodNode>)element.methods) {
			int mCount = Obfuscator.splitDescriptor(m.desc.split("\\)")[0]).size();
			for (MethodNode n : (List<MethodNode>)other.element.methods) {
				int nCount = Obfuscator.splitDescriptor(n.desc.split("\\)")[0]).size();
				if (mCount == nCount) {
					//if (compareMethodBodies(m, n)) {
						matched++;
					//}
				}
			}
		}
		return matched/total;
	}*/
	
	private boolean matchNesting(ClassTree other) {
		ClassTree one = this;
		while (one != null && other != null) {
			if (one.isInner() != other.isInner()) return false;
			one = one.outer();
			other = other.outer();
		}
		return true;
	}
	
	private double matchAttributes(ClassTree other) {
		double result = 0;
		result += similarity(fields(), other.fields());
		result += similarity(methods(), other.methods());
		result += similarity(abstractMethods, other.abstractMethods);
		result += similarity(interfaces(), other.interfaces());
		if (matchNesting(other)) result ++;
		if (isAbstract() == other.isAbstract()) result ++;
		return result/6;
	}
	
	private float similarity(int one, int two) {
		if (one == two) return 1;
		one = Math.abs(one);
		two = Math.abs(two);
		return (float)Math.min(one, two) / (float)Math.max(one, two);
	}
	
	private double similarityInt(ClassTree other) {
		if (isInterface() != other.isInterface()) return 0;
		if (isInner() != other.isInner()) return 0;
		double result = matchAttributes(other) + reverseSimilarity(other);
		if (children.size() == 0 && other.children.size() == 0) {
			return result;
		}
		for (int i = 0; i < children.size() && i < other.children.size(); i++) {
			result += children.get(i).similarityInt(other.children.get(i));
		}
		return result;
	}
	
	private double reverseSimilarity(ClassTree other) {
		double result = Math.min(interfaces(), other.interfaces());
		for (int i = 0; i < interfaces() && i < other.interfaces(); i++) {
			result += interfaces.get(i).reverseSimilarity(other.interfaces.get(i));
		}
		return result;
	}
	
	public int size() {
		int size = 1;
		for (ClassTree i : children) {
			size += i.size();
		}
		return size;
	}
	
	/**
	 * Gets the total number of interface implementations leading down the tree to this one.
	 */
	public int implSize() {
		int size = interfaces.size();
		for (ClassTree i : interfaces) {
			size += i.implSize();
		}
		return size;
	}
	
	/**
	 * Checks if a given name equals this class's name.
	 */
	public boolean classNameEquals(String name) {
		if (name == null) return this == root();
		return name.equals(className);
	}
	
	public String description() {
		return className + " { fields: " + fields() + "; methods: " + methods() + "; interfaces: " + interfaces() + " }";
	}
	
	public String toString() {
		return toString("");
	}
	
	private String toString(String indent) {
		String result = indent + description();
		result += Patterns.LINE_SEPARATOR;
		indent += "\t";
		for (ClassTree i : children) {
			result += i.toString(indent);
		}
		return result;
	}
	
	@Override
	public int compareTo(ClassTree o) {
		int result = Integer.compare(size(), o.size());
		if (result == 0) result = Integer.compare(methods(), o.methods());
		if (result == 0) result = Integer.compare(fields(), o.fields());
		if (result == 0) result = Integer.compare(interfaces(), o.interfaces());
		return result;
	}
	
	public List<String> keySet() {
		List<String> result = new ArrayList<String>();
		buildKeySet(result);
		return result;
	}
	
	private void buildKeySet(List<String> keyset) {
		keyset.add(className);
		for (ClassTree i : children) {
			i.buildKeySet(keyset);
		}
	}
	
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o instanceof ClassTree) return getName().equals(((ClassTree)o).getName());
		return false;
	}
}
