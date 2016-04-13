package com.blazeloader.util.transformers.transformations;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public abstract class Transformation {
	private final TargetSelector targeter;
    public final String targetClass;

    public Transformation(TargetSelector selector, String target) {
    	targeter = selector.initWith(this);
        targetClass = target;
    }
    
    public boolean apply(ClassNode cls) {
    	String dotName = getDotName(cls.name);
    	if (dotName.equals(targetClass)) return targeter.match(cls);
    	return false;
    }
    
    public abstract void transformField(FieldNode node);
    
    public abstract void transformMethod(MethodNode node);
    
    public static String getDotName(String slashName) {
        return slashName.replace('/', '.');
    }
}
