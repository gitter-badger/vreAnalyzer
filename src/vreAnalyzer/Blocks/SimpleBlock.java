package vreAnalyzer.Blocks;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import Patch.Hadoop.ReuseAssets.AssetType;
import soot.SootMethod;
import vreAnalyzer.Elements.CFGNode;

public class SimpleBlock extends CodeBlock{
	public static Map<List<CFGNode>,SimpleBlock>valuepool = new HashMap<List<CFGNode>,SimpleBlock>();
	private static Map<SootMethod,List<SimpleBlock>>methodToBlocks = new HashMap<SootMethod,List<SimpleBlock>>();
	private List<CFGNode>blocks;
	
	
	public SimpleBlock(List<CFGNode> cfgnodes,SootMethod method){
		super();
		blocks = new LinkedList<CFGNode>();
		blocks.addAll(cfgnodes);
		super.setBlocks(blocks);
		super.setMethod(method);
		super.setSootClass(method.getDeclaringClass());
		super.setType(AssetType.Stmt);
		valuepool.put(blocks, this);
		if(methodToBlocks.containsKey(method)){
			methodToBlocks.get(method).add(this);
		}else{
			List<SimpleBlock>blocks = new LinkedList<SimpleBlock>();
			blocks.add(this);
			methodToBlocks.put(method, blocks);
		}
	}
	
	public static SimpleBlock tryToCreate(List<CFGNode> cfgnodes,SootMethod method){
		if(valuepool.containsKey(cfgnodes)){
			SimpleBlock exist = valuepool.get(cfgnodes);
			return exist;
		}else
			return new SimpleBlock(cfgnodes,method);
	}
	public static SimpleBlock tryToCreate(CFGNode cfgnode,SootMethod method){
		if(methodToBlocks.containsKey(method)){
			List<SimpleBlock>blocks = methodToBlocks.get(method);
			for(SimpleBlock block:blocks){
				if(block.getCFGNodes().contains(cfgnode))
					return block;
			}
			return null;
		}else{
			List<CFGNode>list = new LinkedList<CFGNode>();
			list.add(cfgnode);
			return new SimpleBlock(list,method);
		}
	}
}