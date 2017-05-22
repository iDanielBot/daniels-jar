package com.bcd.fraud.bpmn;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormData;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.activiti.engine.impl.form.DateFormType;
import org.activiti.engine.impl.form.LongFormType;
import org.activiti.engine.impl.form.StringFormType;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;

public class ProcessManager {

	private ProcessEngine processEngine;

	public ProcessManager() {
		this.processEngine = loadProcessEngine();
	}

	public void runProcess(ProcessDefinition processDefinition,ProcessInstance processInstance) throws ParseException{
		Scanner scanner = new Scanner(System.in);
		
		TaskService taskService = processEngine.getTaskService();
	    FormService formService = processEngine.getFormService();
	    HistoryService historyService = processEngine.getHistoryService();
		
		 while (processInstance != null && !processInstance.isEnded()) {
		      List<Task> tasks = taskService.createTaskQuery()
		          .taskCandidateGroup("managers").list();
		      System.out.println("Active outstanding tasks: [" + tasks.size() + "]");
		      for (int i = 0; i < tasks.size(); i++) {
		        Task task = tasks.get(i);
		        System.out.println("Processing Task [" + task.getName() + "]");
		        Map<String, Object> variables = new HashMap<String, Object>();
		        FormData formData = formService.getTaskFormData(task.getId());
		        for (FormProperty formProperty : formData.getFormProperties()) {
		          if (StringFormType.class.isInstance(formProperty.getType())) {
		            System.out.println(formProperty.getName() + "?");
		            String value = scanner.nextLine();
		            variables.put(formProperty.getId(), value);
		          } else if (LongFormType.class.isInstance(formProperty.getType())) {
		            System.out.println(formProperty.getName() + "? (Must be a whole number)");
		            Long value = Long.valueOf(scanner.nextLine());
		            variables.put(formProperty.getId(), value);
		          } else if (DateFormType.class.isInstance(formProperty.getType())) {
		            System.out.println(formProperty.getName() + "? (Must be a date m/d/yy)");
		            DateFormat dateFormat = new SimpleDateFormat("m/d/yy");
		            Date value = dateFormat.parse(scanner.nextLine());
		            variables.put(formProperty.getId(), value);
		          } else {
		            System.out.println("<form type not supported>");
		          }
		        }
		        taskService.complete(task.getId(), variables);

		        HistoricActivityInstance endActivity = null;
		        List<HistoricActivityInstance> activities = 
		            historyService.createHistoricActivityInstanceQuery()
		            .processInstanceId(processInstance.getId()).finished()
		            .orderByHistoricActivityInstanceEndTime().asc()
		            .list();
		        for (HistoricActivityInstance activity : activities) {
		          if (activity.getActivityType() == "startEvent") {
		            System.out.println("BEGIN " + processDefinition.getName() 
		                + " [" + processInstance.getProcessDefinitionKey()
		                + "] " + activity.getStartTime());
		          }
		          if (activity.getActivityType() == "endEvent") {
		            // Handle edge case where end step happens so fast that the end step
		            // and previous step(s) are sorted the same. So, cache the end step 
		            //and display it last to represent the logical sequence.
		            endActivity = activity;
		          } else {
		            System.out.println("-- " + activity.getActivityName() 
		                + " [" + activity.getActivityId() + "] "
		                + activity.getDurationInMillis() + " ms");
		          }
		        }
		        if (endActivity != null) {
		          System.out.println("-- " + endActivity.getActivityName() 
		                + " [" + endActivity.getActivityId() + "] "
		                + endActivity.getDurationInMillis() + " ms");
		          System.out.println("COMPLETE " + processDefinition.getName() + " ["
		                + processInstance.getProcessDefinitionKey() + "] " 
		                + endActivity.getEndTime());
		        }
		      }
		      // Re-query the process instance, making sure the latest state is available
		      processInstance = processEngine.getRuntimeService().createProcessInstanceQuery()
		          .processInstanceId(processInstance.getId()).singleResult();
		 }
		 scanner.close();
		    
	}

	public ProcessInstance startProcessInstance(BpmnProcess process) {
		RuntimeService runtimeService = processEngine.getRuntimeService();
		ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(process.getProcessKey());
		System.out.println("Onboarding process started with process instance id ["
				+ processInstance.getProcessInstanceId() + "] key [" + processInstance.getProcessDefinitionKey() + "]");
		return processInstance;
	}

	public ProcessDefinition deployProcess(BpmnProcess process) {
		RepositoryService repositoryService = processEngine.getRepositoryService();
		Deployment deployment = repositoryService.createDeployment().addClasspathResource(process.getPathForDiagram())
				.deploy();
		ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
				.deploymentId(deployment.getId()).singleResult();
		System.out.println("Found process definition [" + processDefinition.getName() + "] with id ["
				+ processDefinition.getId() + "]");
		return processDefinition;
	}

	private final ProcessEngine loadProcessEngine() {
		ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
				.setJdbcUrl("jdbc:h2:mem:activiti;DB_CLOSE_DELAY=1000").setJdbcUsername("sa").setJdbcPassword("")
				.setJdbcDriver("org.h2.Driver")
				.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
		ProcessEngine processEngine = cfg.buildProcessEngine();
		return processEngine;
	}

}
