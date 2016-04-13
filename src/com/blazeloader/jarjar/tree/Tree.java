package com.blazeloader.jarjar.tree;

import java.util.List;

public interface Tree<T extends Tree<T, I>, I> extends Comparable<T> {
	
	public T addChild(T child);
	
	/**
	 * Gets all the direct children of this tree element.
	 */
	public List<T> children();
	
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
}
