package com.blazeloader.jarjar.tree;

import java.util.List;

public interface Tree<T extends Tree<T, I>, I> extends Comparable<T> {
	
	/**
	 * Sorts the elements in this tree. Returns itself for convenience.
	 */
	public T sort();
	
	/**
	 * Adds a new element as a child as this one and returns the child.
	 */
	public T addChild(T child);
	
	/**
	 * Gets all the direct children of this tree element.
	 */
	public List<T> children();
	
	/**
	 * Gets a list of the keys for every element below and including this one.
	 */
	public List<String> keySet();
	
	/**
	 * Gets this elements direct parent.
	 */
	public T parent();
	
	/**
	 * Gets the very topmost element of the tree.
	 */
	public T root();
	
	/**
	 * Finds a class tree node from it's name starting at this element.
	 */
	public T lookup(I name);
	
	/**
	 * Gets the total number of elements below and including this tree node.
	 */
	public int size();
	
	/**
	 * Gets a string detailing only this element.
	 * @return
	 */
	public String description();
}
