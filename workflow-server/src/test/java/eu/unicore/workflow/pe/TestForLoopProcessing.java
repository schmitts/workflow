package eu.unicore.workflow.pe;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.chemomentum.dsws.ConversionResult;
import org.json.JSONObject;
import org.junit.Ignore;
import org.junit.Test;

import de.fzj.unicore.uas.util.Pair;
import de.fzj.unicore.xnjs.ems.ProcessingException;
import de.fzj.unicore.xnjs.util.IOUtils;
import eu.unicore.workflow.json.Converter;
import eu.unicore.workflow.pe.iterators.ChunkedFileIterator;
import eu.unicore.workflow.pe.iterators.FileSetIterator;
import eu.unicore.workflow.pe.iterators.FileSetIterator.FileSet;
import eu.unicore.workflow.pe.iterators.ResolverFactory;
import eu.unicore.workflow.pe.iterators.ResolverFactory.Resolver;
import eu.unicore.workflow.pe.iterators.ValueSetIterator;
import eu.unicore.workflow.pe.iterators.VariableSetIterator;
import eu.unicore.workflow.pe.model.ActivityGroup;
import eu.unicore.workflow.pe.model.Condition;
import eu.unicore.workflow.pe.model.ForGroup;
import eu.unicore.workflow.pe.model.Iterate;
import eu.unicore.workflow.pe.model.PEWorkflow;
import eu.unicore.workflow.pe.model.ScriptCondition;
import eu.unicore.workflow.pe.model.Transition;
import eu.unicore.workflow.pe.persistence.PEStatus;
import eu.unicore.workflow.pe.util.TestActivity;
import eu.unicore.workflow.pe.xnjs.Constants;
import eu.unicore.workflow.pe.xnjs.Validate;
import eu.unicore.workflow.xnjs.TestBase;

public class TestForLoopProcessing extends TestBase {
	
	@Test
	public void testValuesIteration() throws Exception {
		String file="src/test/resources/json/foreach.json";
		String wfID=UUID.randomUUID().toString();
		JSONObject json = new JSONObject(IOUtils.readFile(new File(file)));
		ConversionResult res = new Converter(true).convert(wfID, json);
		assert !res.hasConversionErrors();
		PEWorkflow wf = res.getConvertedWorkflow();
		doProcess(wf);
	}
	
	@Test
	public void testVariableSetIteration() throws Exception {
		String file="src/test/resources/json/foreach_varset.json";
		String wfID=UUID.randomUUID().toString();
		JSONObject json = new JSONObject(IOUtils.readFile(new File(file)));
		ConversionResult res = new Converter(true).convert(wfID, json);
		assert !res.hasConversionErrors(): String.valueOf(res.getConversionErrors());
		PEWorkflow wf = res.getConvertedWorkflow();
		doProcess(wf);
	}
	
	@Test
	public void testIterateOverValueSet()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		String wfID=UUID.randomUUID().toString();
		String id="for1";
		String[] iterationValues=new String[]{"a","b","c"};
		PEWorkflow wf=new PEWorkflow(wfID);
		Iterate iter=new ValueSetIterator(iterationValues);
		ActivityGroup body = new ActivityGroup("for1-body", wfID, iter);
		TestActivity a1=new TestActivity("a1",wfID);
		body.setActivities(a1);
		ForGroup fl=new ForGroup(id, wfID, body);
		wf.setActivities(fl);

		doProcess(wf,id);

		assert(Validate.wasInvoked("a1"));
		Integer i=Validate.getInvocations("a1");
		assert(iterationValues.length==i.intValue());

		//check final status

		List<PEStatus>status=getStatus(wfID, id);
		System.out.println("Final status of <"+id+">: "+status);
	}


	@Test
	public void testIterateOverVariableSet()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		String wfID=UUID.randomUUID().toString();
		String id="for1";

		PEWorkflow wf=new PEWorkflow(wfID);

		VariableSetIterator.VariableSet varSet=new VariableSetIterator.VariableSet(
				"C","0","C<5", "C++", "INTEGER");

		VariableSetIterator iter=new VariableSetIterator(wfID, varSet);
		ActivityGroup body = new ActivityGroup("for1-body", wfID, iter);
		TestActivity a1=new TestActivity("a1",wfID);
		body.setActivities(a1);
		ForGroup fl=new ForGroup(id,wfID,body);
		wf.setActivities(fl);

		doProcess(wf,id);

		assert(Validate.wasInvoked("a1"));
		Integer i=Validate.getInvocations("a1");
		assert (5==i.intValue()) : "iterations: "+i;

		//check final status

		List<PEStatus>status=getStatus(wfID, id);
		System.out.println("Final status of <"+id+">: "+status);
	}

	@Test
	public void testIterateOverFileSet()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		ResolverFactory.registerResolver(R1.class);
		FileSet fs=new FileSet("test/", null, null, false, false);
		FileSetIterator fsi=new FileSetIterator(null,fs);
		fsi.setIteratorName("ForEach_Iterator");

		String wfID=UUID.randomUUID().toString();

		PEWorkflow wf=new PEWorkflow(wfID);
		ActivityGroup body = new ActivityGroup("for1-body", wfID, fsi);
		TestActivity a1 = new TestActivity("a1",wfID);
		body.setActivities(a1);
		ForGroup fl = new ForGroup("for1",wfID,body);
		wf.setActivities(fl);

		doProcess(wf);

		assert Validate.wasInvoked("a1");
		assert 10==Validate.getInvocations("a1");

	}
	
	static long size = 0l;
	static int total = 10;
	
	public static class R1 implements Resolver {

		public boolean acceptBase(String base) {
			return true;
		}
		public Collection<Pair<String, Long>> resolve(String workflowID, FileSet fileset)
				throws ProcessingException {
			ArrayList<Pair<String, Long>>results=new ArrayList<Pair<String, Long>>();
			for(int i=0;i<total;i++){
				results.add(new Pair<String, Long>("file_"+i, size));
			}
			return results;
		}
	}

	@Test
	public void testIterateOverChunkedFileSet()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		final int chunkSize=2;

		ResolverFactory.registerResolver(R1.class);
		FileSet fs=new FileSet("test/", null, null, false, false);
		FileSetIterator fsi=new FileSetIterator(null,fs);

		ChunkedFileIterator chunkedIterator=new ChunkedFileIterator(fsi,chunkSize);
		chunkedIterator.setIteratorName("ForEach_Iterator");

		String wfID=UUID.randomUUID().toString();

		PEWorkflow wf=new PEWorkflow(wfID);
		ActivityGroup body = new ActivityGroup("for1-body", wfID, chunkedIterator);
		TestActivity a1=new TestActivity("a1", wfID);
		body.setActivities(a1);
		ForGroup fl=new ForGroup("for1",wfID,body);
		wf.setActivities(fl);

		doProcess(wf);

		assert Validate.wasInvoked("a1");

	}

	@Test
	public void testChunkedFileSetWithDynamicSize()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		total=10;
		size=100l;
		
		final int chunkSize=2;

		ResolverFactory.registerResolver(R1.class);
		FileSet fs=new FileSet("test/", null, null, false, false);
		FileSetIterator fsi=new FileSetIterator(null,fs);

		// compute chunk size as number of files per chunk
		String expr="return TOTAL_NUMBER / 5;";
		ChunkedFileIterator chunkedIterator=new ChunkedFileIterator(fsi,expr,ChunkedFileIterator.Type.NUMBER);
		chunkedIterator.setIteratorName("ForEach_Iterator");

		String wfID=UUID.randomUUID().toString();

		PEWorkflow wf=new PEWorkflow(wfID);
		ActivityGroup body = new ActivityGroup("for1-body", wfID, chunkedIterator);
		TestActivity a1 = new TestActivity("a1",wfID);
		body.setActivities(a1);
		ForGroup fl = new ForGroup("for1",wfID,body);
		
		
		wf.setActivities(fl);

		doProcess(wf);

		assert Validate.wasInvoked("a1");
		assert total/chunkSize==Validate.getInvocations("a1");
	}


	@Test
	public void testChunkedFileSetWithDynamicSize2()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		total=10;
		size=1024l;
		
		final int chunkSize=2;

		ResolverFactory.registerResolver(R1.class);
		FileSet fs=new FileSet("test/", null, null, false, false);
		FileSetIterator fsi=new FileSetIterator(null,fs);

		// compute chunk size in kbytes
		String expr="return TOTAL_SIZE / 5;";
		ChunkedFileIterator chunkedIterator=new ChunkedFileIterator(fsi,expr,ChunkedFileIterator.Type.SIZE);
		chunkedIterator.setIteratorName("ForEach_Iterator");

		String wfID=UUID.randomUUID().toString();

		PEWorkflow wf=new PEWorkflow(wfID);
		ActivityGroup body = new ActivityGroup("for1-body", wfID, chunkedIterator);
		TestActivity a1 = new TestActivity("a1",wfID );
		body.setActivities(a1);
		ForGroup fl = new ForGroup("for1",wfID,body);
		wf.setActivities(fl);

		doProcess(wf);

		assert Validate.wasInvoked("a1");
		assert total/chunkSize==Validate.getInvocations("a1");
	}


	@Test
	public void testChunkedFileSetWithDynamicSize3()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		total=10;
		size=1024l;
		final int chunkSize=2;

		ResolverFactory.registerResolver(R1.class);
		FileSet fs=new FileSet("test/", null, null, false, false);
		FileSetIterator fsi=new FileSetIterator(null,fs);

		// compute chunk size in kbytes
		String expr="if(TOTAL_SIZE > 5) return 2; \n" +
				"return 1;";
		ChunkedFileIterator chunkedIterator=new ChunkedFileIterator(fsi,expr,ChunkedFileIterator.Type.NUMBER);
		chunkedIterator.setIteratorName("ForEach_Iterator");

		String wfID=UUID.randomUUID().toString();
		PEWorkflow wf=new PEWorkflow(wfID);

		ActivityGroup body = new ActivityGroup("for1-body", wfID, chunkedIterator);
		TestActivity a1 = new TestActivity("a1",wfID);
		body.setActivities(a1);
		ForGroup fl = new ForGroup("for1",wfID,body);
		
		wf.setActivities(fl);

		doProcess(wf);

		assert Validate.wasInvoked("a1");
		assert total/chunkSize==Validate.getInvocations("a1");
	}

	@Test @Ignore
	public void testLargeSet()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		String wfID=UUID.randomUUID().toString();

		int N=500;
		String[] iterationValues=new String[N];
		for(int i=0;i<N;i++){
			iterationValues[i]=String.valueOf(i);
		}
		PEWorkflow wf=new PEWorkflow(wfID);
		Iterate iter=new ValueSetIterator(iterationValues);
		ActivityGroup ag=new ActivityGroup("grp1",wfID,iter);
		TestActivity a1=new TestActivity("a1",wfID);
		ag.setActivities(a1);
		ForGroup fl=new ForGroup("for1", wfID, ag);
		wf.setActivities(fl);

		doProcess(wf);

		assert(Validate.wasInvoked("a1"));
		Integer i=Validate.getInvocations("a1");
		System.out.println("Processed <"+i+"> iterations.");
		assert(iterationValues.length==i.intValue());
	}


	@Test
	public void testNested()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		String wfID=UUID.randomUUID().toString();
		PEWorkflow wf=new PEWorkflow(wfID);

		String[] iterationValuesOuter=new String[]{"1","2","3"};
		Iterate outerIterate=new ValueSetIterator(iterationValuesOuter);
		String[] iterationValuesInner=new String[]{"a","b","c"};
		Iterate innerIterate=new ValueSetIterator(iterationValuesInner);
		TestActivity a1=new TestActivity("theActivity",wfID);


		ActivityGroup innerBody=new ActivityGroup("innerBody",wfID);
		innerBody.setLoopIteratorName("innerI");
		innerBody.setActivities(a1);
		innerBody.setIterate(innerIterate);

		ForGroup inner=new ForGroup("innerForLoop",wfID,innerBody);

		ActivityGroup outerBody=new ActivityGroup("outerBody",wfID);
		outerBody.setLoopIteratorName("outerI");
		outerBody.setActivities(inner);
		outerBody.setIterate(outerIterate);
		ForGroup outer=new ForGroup("outerForLoop",wfID,outerBody);
		wf.setActivities(outer);

		doProcess(wf);

		assert Validate.wasInvoked("theActivity");
		Integer i=Validate.getInvocations("theActivity");
		assert i==iterationValuesOuter.length*iterationValuesInner.length;
	}

	@Test
	public void testNestedWithInnerCondition()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();

		String wfID=UUID.randomUUID().toString();
		PEWorkflow wf=new PEWorkflow(wfID);

		String[] iterationValuesOuter=new String[]{"1","2","3"};
		Iterate outerIterate=new ValueSetIterator(iterationValuesOuter);
		String[] iterationValuesInner=new String[]{"a","b","c"};
		Iterate innerIterate=new ValueSetIterator(iterationValuesInner);

		TestActivity a1=new TestActivity("inner-a1",wfID);
		TestActivity a2=new TestActivity("inner-a2",wfID);
		String innerScript="\"1:::2\".equals(new "+Evaluator.class.getName()
				+"(\"wf\", "+Constants.VAR_KEY_CURRENT_TOTAL_ITERATION+").getIteration())";
		Condition c=new ScriptCondition("inner_conditon",wfID,innerScript);
		Transition t1=new Transition("inner-a1->inner-a2",wfID,"inner-a1", "inner-a2",c);

		ActivityGroup innerBody=new ActivityGroup("innerBody",wfID);
		innerBody.setLoopIteratorName("innerI");
		innerBody.setActivities(a1,a2);
		innerBody.setTransitions(t1);
		innerBody.setIterate(innerIterate);

		ForGroup inner=new ForGroup("innerForLoop",wfID,innerBody);

		ActivityGroup outerBody=new ActivityGroup("outerBody",wfID);
		outerBody.setLoopIteratorName("outerI");
		outerBody.setActivities(inner);
		outerBody.setIterate(outerIterate);
		ForGroup outer=new ForGroup("outerForLoop",wfID,outerBody);
		wf.setActivities(outer);

		doProcess(wf);

		assert Validate.wasInvoked("inner-a2");
		Integer i=Validate.getInvocations("inner-a2");
		assert i==1 : "Too many invocations: "+i;
	}


	@Test
	public void testIterationValue()throws Exception{
		Validate.clear();
		PEConfig.getInstance().getPersistence().removeAll();
		String wfID=UUID.randomUUID().toString();
		PEWorkflow wf=new PEWorkflow(wfID);

		String id="for1";

		String[] iterationValues=new String[]{"a","b","c"};

		Iterate iter=new ValueSetIterator(iterationValues);

		ActivityGroup ag=new ActivityGroup("for_body",wfID,iter);
		TestActivity a1=new TestActivity("a1",wfID);
		TestActivity a2=new TestActivity("a2",wfID);
		Condition c=new ScriptCondition("c1", wfID, "CURRENT_TOTAL_ITERATOR!=null");
		Transition t=new Transition("a1->a2",wfID,"a1","a2",c);
		ag.setActivities(a1,a2);
		ag.setTransitions(t);
		ForGroup fl=new ForGroup(id,wfID,ag);
		wf.setActivities(fl);

		doProcess(wf);

		assert(Validate.wasInvoked("a1"));
		Integer i=Validate.getInvocations("a1");
		assert(iterationValues.length==i.intValue());

		//check final status

		List<PEStatus>status=getStatus(wfID, id);
		System.out.println("Final status of <"+id+">: "+status);
	}
}