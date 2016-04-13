package com.blazeloader.util.transformers.transformations;

import org.objectweb.asm.tree.ClassNode;

public interface TargetSelector {
	public TargetSelector initWith(Transformation transformation);
	
	public boolean match(ClassNode cls);
}
