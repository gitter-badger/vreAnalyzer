package Patch.Hadoop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import Patch.Hadoop.Pipelines.ConfigurePipelines;
import Patch.Hadoop.Pipelines.JobExternalConfigurePipelines;
import Patch.Hadoop.Pipelines.MapperPipelines;
import Patch.Hadoop.Pipelines.ReducerPipelines;
import Patch.Hadoop.Pipelines.SingleConfigurePipeline;
import Patch.Hadoop.Pipelines.SingleJobExternalConfigurePipeline;
import Patch.Hadoop.Pipelines.SingleMapperPipeline;
import Patch.Hadoop.Pipelines.SingleReducerPipeline;
import soot.Body;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Value;
import soot.ValueBox;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.util.Chain;
import vreAnalyzer.ControlFlowGraph.DefUse.CFGDefUse;
import vreAnalyzer.ControlFlowGraph.DefUse.NodeDefUses;
import vreAnalyzer.ControlFlowGraph.DefUse.Use.Use;
import vreAnalyzer.Elements.CFGNode;
import vreAnalyzer.ProgramFlow.ProgramFlowBuilder;
	
public class ProjectParser {
	
	public static ProjectParser instance = null;
	private Body sootmethodBody = null;
	private SootMethod sootmethod = null;
	private SootClass sootcls = null;
	private CFGDefUse cfggraph;
	private boolean verbose = true;
	
	/* 
	 * Store all jobs that defined in the main method,
	 * including locals and fields 
	 */
	
	private Map<Object,JobHub>jobtoHub = null;
	private Map<Integer,Object>indextoJob = null;
	

	public static ProjectParser inst(){
		if(instance==null)
			instance = new ProjectParser();
		return instance;
	}
	public JobHub getjobHub(Value job){
		return jobtoHub.get(job);
	}
	public void ClassParser(){
		jobtoHub = new HashMap<Object,JobHub>();
		indextoJob = new HashMap<Integer,Object>();
		// run all the application methods
		for(SootMethod sm:ProgramFlowBuilder.inst().getAppConcreteMethods()){
			cfggraph = (CFGDefUse) ProgramFlowBuilder.inst().getCFG(sm);
			sootcls = sm.getDeclaringClass();
			sootmethodBody = sm.retrieveActiveBody();
			sootmethod = sm;
			Parse();
		}
	}
	public void Parse(){
		// 1. Get the CFG
		int index = 0;
		List<CFGNode> allnodes = cfggraph.getNodes();
		
		// 2.0 Find mapper and reducer in lib class set
		SootClass libMapper = ProgramFlowBuilder.inst().findLibClassByName("org.apache.hadoop.mapreduce.Mapper");
		SootClass libReducer = ProgramFlowBuilder.inst().findLibClassByName("org.apache.hadoop.mapreduce.Reducer");
				
		// 2. Find all Jobs and associated with mapper and reducer classes
		for(int i = 0;i < allnodes.size();i++){
			NodeDefUses cfgNode = (NodeDefUses) allnodes.get(i);
			if(!cfgNode.isSpecial()){
				Stmt stmt = (Stmt) cfgNode.getStmt();
				if (!(stmt instanceof IdentityStmt)) {
					
					if(stmt.containsFieldRef()){
						if(stmt.getFieldRef().getField()!=null){
							FieldRef rf = stmt.getFieldRef();
							SootField key = rf.getField();
						
							if(rf.getType().toString().equals("org.apache.hadoop.mapreduce.Job")){
								if(!jobtoHub.containsKey(key)){
									jobtoHub.put(key, new JobHub(key));
									indextoJob.put(index,key);
									index++;
									
								}
								
								// Find embedded mapper setting and reducer setting
								if(stmt.containsInvokeExpr()){
									
									InvokeExpr invokeExpr = stmt.getInvokeExpr();
									
									// Set a Mapper;
									if(invokeExpr.getMethod().getName().equals("setMapperClass")){ 
										
										String mapper = invokeExpr.getArg(0).toString();
										mapper = mapper.substring(mapper.indexOf("\"")+1, mapper.lastIndexOf("\""));
										mapper = mapper.replaceAll("/", ".");
										// Get lib Class for this setting
										SootClass appMapper = ProgramFlowBuilder.inst().findAppClassByNameAndSuper(mapper, libMapper);			
										
										jobtoHub.get(key).setMapperClass(appMapper);
										MapperPipelines.inst().createNewSingleMap(key,appMapper);
									}
									
									// Set a Reducer;
									else if(invokeExpr.getMethod().getName().equals("setReducerClass")){
										
										String reducer = invokeExpr.getArg(0).toString();									
										reducer = reducer.substring(reducer.indexOf("\"")+1, reducer.lastIndexOf("\""));
										reducer = reducer.replaceAll("/", ".");
										// Get lib Class for this setting
										SootClass appReducer = ProgramFlowBuilder.inst().findAppClassByNameAndSuper(reducer, libReducer);
																		
										jobtoHub.get(key).setReducerClass(appReducer);
										ReducerPipelines.inst().createNewSingleReducer(key,appReducer);
									}
									
									// set a combiner
									else if(invokeExpr.getMethod().getName().equals("setCombinerClass")){
										String combiner = invokeExpr.getArg(0).toString();									
										combiner = combiner.substring(combiner.indexOf("\"")+1, combiner.lastIndexOf("\""));
										combiner = combiner.replaceAll("/", ".");
										// Get lib Class for this setting
										// Get lib Class for this setting
										SootClass appCombiner = ProgramFlowBuilder.inst().findAppClassByNameAndSuper(combiner, libReducer);
																		
										jobtoHub.get(key).setCombinerClass(appCombiner);
										
										
									}
									
									// skip these settings for now
									else if(invokeExpr.getMethod().getName().equals("setJarByClass")||
											invokeExpr.getMethod().getName().equals("waitForCompletion")||
											invokeExpr.getMethod().getName().equals("setOutputKeyClass")||
											invokeExpr.getMethod().getName().equals("setOutputValueClass")){
										continue;
									}
									
									// Set the input path 
									else if(invokeExpr.getMethod().getName().equals("addInputPath")){
										// there are two argus: 
										// (1) the job
										// (2) the path
										Value input = (Value) invokeExpr.getArg(1);
										jobtoHub.get(key).setInputPath(input);
									}
									
									// Set the output path
									else if(invokeExpr.getMethod().getName().equals("setOutputPath")){
										// there are two argus: 
										// (1) the job
										// (2) the path
										Value output = (Value) invokeExpr.getArg(1);
										jobtoHub.get(key).setOutPath(output);
									}// Other settings of this job
									else{
										if(JobExternalConfigurePipelines.inst().getEnConfigurePipeline(key)==null)
											JobExternalConfigurePipelines.inst().createNewJobExternalConfigurePipeline(key, sootmethod, cfgNode);
										else
											JobExternalConfigurePipelines.inst().getEnConfigurePipeline(key).addSettingNode(cfgNode);
									}
								}
							}
						}
					}
					for(ValueBox vbox:stmt.getUseAndDefBoxes()){
						Value key = vbox.getValue();
						// Here, we get rid of jimple temporary local
						if(key.getType().toString().equals("org.apache.hadoop.mapreduce.Job") &&
								!key.toString().startsWith("$") &&
								(sootmethodBody.getLocals().contains(key))){
							if(!jobtoHub.containsKey(key)){
								jobtoHub.put(key, new JobHub(key));
								indextoJob.put(index,key);
								index++;
								
							}
							
							// Find embedded mapper setting and reducer setting
							if(stmt.containsInvokeExpr()){
								
								InvokeExpr invokeExpr = stmt.getInvokeExpr();
								
								// Set a Mapper;
								if(invokeExpr.getMethod().getName().equals("setMapperClass")){ 
									
									String mapper = invokeExpr.getArg(0).toString();
									mapper = mapper.substring(mapper.indexOf("\"")+1, mapper.lastIndexOf("\""));
									mapper = mapper.replaceAll("/", ".");
									// Get lib Class for this setting
									SootClass appMapper = ProgramFlowBuilder.inst().findAppClassByNameAndSuper(mapper, libMapper);			
									
									jobtoHub.get(key).setMapperClass(appMapper);
									MapperPipelines.inst().createNewSingleMap(key,appMapper);
								}
								
								// Set a Reducer;
								else if(invokeExpr.getMethod().getName().equals("setReducerClass")){
									
									String reducer = invokeExpr.getArg(0).toString();									
									reducer = reducer.substring(reducer.indexOf("\"")+1, reducer.lastIndexOf("\""));
									reducer = reducer.replaceAll("/", ".");
									// Get lib Class for this setting
									SootClass appReducer = ProgramFlowBuilder.inst().findAppClassByNameAndSuper(reducer, libReducer);
																	
									jobtoHub.get(key).setReducerClass(appReducer);
									ReducerPipelines.inst().createNewSingleReducer(key,appReducer);
								}
								
								// set a combiner
								else if(invokeExpr.getMethod().getName().equals("setCombinerClass")){
									String combiner = invokeExpr.getArg(0).toString();									
									combiner = combiner.substring(combiner.indexOf("\"")+1, combiner.lastIndexOf("\""));
									combiner = combiner.replaceAll("/", ".");
									// Get lib Class for this setting
									// Get lib Class for this setting
									SootClass appCombiner = ProgramFlowBuilder.inst().findAppClassByNameAndSuper(combiner, libReducer);
																	
									jobtoHub.get(key).setCombinerClass(appCombiner);
									
									
								}
								
								// skip these settings for now
								else if(invokeExpr.getMethod().getName().equals("setJarByClass")||
										invokeExpr.getMethod().getName().equals("waitForCompletion")||
										invokeExpr.getMethod().getName().equals("setOutputKeyClass")||
										invokeExpr.getMethod().getName().equals("setOutputValueClass")){
									continue;
								}
								
								// Set the input path 
								else if(invokeExpr.getMethod().getName().equals("addInputPath")){
									// there are two argus: 
									// (1) the job
									// (2) the path
									Value input = (Value) invokeExpr.getArg(1);
									jobtoHub.get(key).setInputPath(input);
								}
								
								// Set the output path
								else if(invokeExpr.getMethod().getName().equals("setOutputPath")){
									// there are two argus: 
									// (1) the job
									// (2) the path
									Value output = (Value) invokeExpr.getArg(1);
									jobtoHub.get(key).setOutPath(output);
								}// Other settings of this job
								else{
									if(JobExternalConfigurePipelines.inst().getEnConfigurePipeline(key)==null)
										JobExternalConfigurePipelines.inst().createNewJobExternalConfigurePipeline(key, sootmethod, cfgNode);
									else
										JobExternalConfigurePipelines.inst().getEnConfigurePipeline(key).addSettingNode(cfgNode);
								}
							}
							
							
						}
					}
				}
			}
		}
		
		
		
		
		
			
		
		
		
	}
	public void showCommons(){
		System.out.println("[vreAnalyzer] Num. of jobs detected:\t"+jobtoHub.keySet().size());
		// 4. Start common assets analysis on Mappers and Reducers
		for(int i = 0;i < jobtoHub.keySet().size();i++){
					
			SingleMapperPipeline srcMapper = MapperPipelines.inst().getMapperPipeline(indextoJob.get(i));
			SingleReducerPipeline srcReducer = ReducerPipelines.inst().getReducerPipeline(indextoJob.get(i));
			SingleConfigurePipeline srcConfigure = ConfigurePipelines.inst().getConfigurePipeline(indextoJob.get(i));
			SingleJobExternalConfigurePipeline srcJobexConfigure = JobExternalConfigurePipelines.inst().getEnConfigurePipeline(indextoJob.get(i));
					
			for(int j = i+1; j < jobtoHub.keySet().size();j++){
				if(verbose)
					System.out.println("Comparing job: #"+i+" with #"+j+":");
				SingleMapperPipeline otherMapper = MapperPipelines.inst().getMapperPipeline(indextoJob.get(j));
				SingleReducerPipeline otherReducer = ReducerPipelines.inst().getReducerPipeline(indextoJob.get(j));
				SingleConfigurePipeline otherConfigure = ConfigurePipelines.inst().getConfigurePipeline(indextoJob.get(j));
				SingleJobExternalConfigurePipeline otherJobexConfigure = JobExternalConfigurePipelines.inst().getEnConfigurePipeline(indextoJob.get(j));
						
						
				// Get the common asset of other configuration
				Map<NodeDefUses,NodeDefUses> commonEx = srcJobexConfigure.getCommonAsset(otherJobexConfigure);
				if(verbose){
					if(commonEx.size()==0)
						System.out.println("No common parts in extra-configue");
					else{
						System.out.println("The common parts in extra configuration are:");
						for(Map.Entry<NodeDefUses, NodeDefUses>entry : commonEx.entrySet()){
							System.out.println("Node in src:\t|"+entry.getKey().toString()+"\n Node in other:\t|"+entry.getValue().toString());
						}
					}
				}
						
				// Get the common asset of configuration
				CFGNode[] configcommons = srcConfigure.getCommonAsset(otherConfigure);
				if(verbose){
					SootClass configure = jobtoHub.get(indextoJob.get(i)).getConfigureClass();
					SootClass otherconfigure = jobtoHub.get(indextoJob.get(j)).getConfigureClass();
					System.out.println("StartIndex and EndIndex Configuration in \t"+configure.getName()+"\t is");
					System.out.println("Start:\t"+configcommons[0].toString()+"\t|\tEnd:\t"+configcommons[1].toString());
					System.out.println("StartIndex and EndIndex Configuration in \t"+otherconfigure.getName()+"\t is");
					System.out.println("Start:\t"+configcommons[2].toString()+"\t|\tEnd:\t"+configcommons[3].toString());
				}
						
						
				// Get the common asset of mapper
				CFGNode[] mappercommons = srcMapper.getCommonAsset(otherMapper);
				if(verbose){
					SootClass mapper = jobtoHub.get(indextoJob.get(i)).getMapperClass();
					SootClass othermapper = jobtoHub.get(indextoJob.get(j)).getMapperClass();
					System.out.println("StartIndex and EndIndex Map in \t"+mapper.getName()+"\t is");
					System.out.println("Start:\t"+mappercommons[0].toString()+"\t|\tEnd:\t"+mappercommons[1].toString());
					System.out.println("StartIndex and EndIndex Map in \t"+othermapper.getName()+"\t is");
					System.out.println("Start:\t"+mappercommons[2].toString()+"\t|\tEnd:\t"+mappercommons[3].toString());
				}
						
						
						
				// Get the common asset of reduce
				CFGNode[] reducercommons = srcReducer.getCommonAsset(otherReducer);
				if(verbose){
					SootClass reducer = jobtoHub.get(indextoJob.get(i)).getReducerClass();
					SootClass otherreducer = jobtoHub.get(indextoJob.get(j)).getReducerClass();
					System.out.println("StartIndex and EndIndex Map in \t"+reducer.getName()+"\t is");
					System.out.println("Start:\t"+reducercommons[0].toString()+"\t|\tEnd:\t"+reducercommons[1].toString());
					System.out.println("StartIndex and EndIndex Map in \t"+otherreducer.getName()+"\t is");
					System.out.println("Start:\t"+reducercommons[2].toString()+"\t|\tEnd:\t"+reducercommons[3].toString());
				}
				if(verbose)
					System.out.println("Finish comparing job: #"+i+" with #"+j+":");
						
			}
		}
	}
	
	
	
}