package com.blazeloader.bl.obf;

import net.acomputerdog.OBFUtil.map.ObfMapSrg;
import net.acomputerdog.OBFUtil.map.TargetType;
import net.acomputerdog.OBFUtil.map.ObfMapSrg.Entry;
import net.acomputerdog.OBFUtil.table.DirectOBFTableSRG;

/**
 * BlazeLoader OBFTable that allows converting stored data into BLOBFs.
 * Provided methods automatically cache calls, so repeated calls with the same parameters will return the same BLOBF object.
 */
public class BLOBFTable<T extends BLOBFTable<T>.BLOBFMapping> extends DirectOBFTableSRG<BLOBF, T> {
	
    public BLOBFTable() {
        super();
    }
    
    public BLOBF getBLOBF(String name, TargetType type, OBFLevel level) {
    	T map = tableMappings.getChecked(type);
    	if (!map.hasType(name, level)) throw new IllegalArgumentException("Unrecognised Obfuscation String: " + level.toString() + "@" + name + " for TargetType: " + type.toString());
    	Entry item;
    	switch (level) {
			case SRG: item = map.bySrg(name); break;
			case MCP: item = map.byDeobf(name); break;
			default: item = map.byObf(name); break;
		}
    	if (!(item instanceof BLOBF)) {
    		return map.remap(item, new BLOBF(item.obf(), item.srg(), item.deObf()));
    	}
    	return (BLOBF)item;
    }
    
    public boolean hasType(String name, TargetType type, OBFLevel level) {
    	return tableMappings.containsKey(type) && tableMappings.getChecked(type).hasType(name, level);
    }
    
    protected T createMap() {
    	return (T)new BLOBFMapping();
    }
    
    protected class BLOBFMapping extends MappingSrg {
    	public BLOBF remap(ObfMapSrg.Entry entry, BLOBF obf) {
    		obfuscated.put(entry.obf(), obf);
    		deobfuscated.put(entry.deObf(), obf);
    		searge.put(entry.srg(), obf);
    		return obf;
    	}
    	
    	public boolean hasType(String name, OBFLevel level) {
    		switch (level) {
				case SRG: return hasSrg(name);
				case MCP: return hasDeObf(name);
				default: return hasObf(name);
			}
    	}
    }
}
