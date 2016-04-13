package com.blazeloader.util.transformers.mapping;

import com.blazeloader.util.transformers.transformations.Transformation;

import java.util.List;

import org.objectweb.asm.tree.ClassNode;

public interface TransformationMap {
	/**
	 * Gets a list of transformations that apply to the named class.
	 * @param className	Fully qualified name of the target class
	 */
    public List<Transformation> getClassMap(String className);
    
    /**
     * Gets the total number of transformations that apply to the named class.
     * @param className	Fully qualified name of the target class
     */
    public int getNumTransformations(String className);
    
    /**
     * Adds a new transformation to this map.
     */
    public void addTransformation(Transformation transformation);
    
    /**
     * Applies all applicable transformations to the given classnode.
     * 
     * @param className	Name of class being transformed.
     * @param node		The classNode
     * @return	True if any transformations were done, and the class should be regenerated.
     */
    public boolean applyAll(String className, ClassNode node);
}
