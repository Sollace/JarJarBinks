package com.blazeloader.util.transformers.mapping;

import com.blazeloader.util.transformers.transformations.Transformation;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.objectweb.asm.tree.ClassNode;

public class DefaultTransformationMap extends HashMap<String, List<Transformation>> implements TransformationMap {
    
    public List<Transformation> getClassMap(String className) {
        if (className == null) {
            throw new IllegalArgumentException("Class name must not be null!");
        }
        List<Transformation> trans = get(className);
        if (trans == null) {
            put(className, trans = new LinkedList<Transformation>());
        }
        return trans;
    }
    
    public int getNumTransformations(String className) {
        return getClassMap(className).size();
    }
    
    public void addTransformation(Transformation transformation) {
        if (transformation == null || transformation.targetClass == null) {
            throw new IllegalArgumentException("Class name and transformation must not be null!");
        }
        getClassMap(transformation.targetClass).add(transformation);
    }
    
    public boolean applyAll(String className, ClassNode node) {
    	boolean result = false;
    	for (Transformation trans : getClassMap(className)) {
            result |= trans.apply(node);
        }
    	return result;
    }
}
