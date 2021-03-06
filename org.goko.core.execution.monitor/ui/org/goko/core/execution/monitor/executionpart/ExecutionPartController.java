package org.goko.core.execution.monitor.executionpart;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.swt.SWT;
import org.goko.common.bindings.AbstractController;
import org.goko.core.common.exception.GkException;
import org.goko.core.common.measure.quantity.Time;
import org.goko.core.common.measure.quantity.TimeUnit;
import org.goko.core.execution.IGCodeExecutionTimeService;
import org.goko.core.execution.monitor.event.ExecutionServiceWorkspaceEvent;
import org.goko.core.gcode.element.IGCodeProvider;
import org.goko.core.gcode.execution.ExecutionState;
import org.goko.core.gcode.execution.ExecutionToken;
import org.goko.core.gcode.execution.ExecutionTokenState;
import org.goko.core.gcode.service.IExecutionService;
import org.goko.core.gcode.service.IGCodeExecutionListener;
import org.goko.core.gcode.service.IGCodeProviderRepository;
import org.goko.core.gcode.service.IGCodeProviderRepositoryListener;
import org.goko.core.log.GkLog;
import org.goko.core.workspace.service.IWorkspaceEvent;
import org.goko.core.workspace.service.IWorkspaceListener;
import org.goko.core.workspace.service.IWorkspaceService;

/**
 * Part displaying execution state and allowing to control it
 *
 * @author Psyko
 */
public class ExecutionPartController extends AbstractController<ExecutionPartModel> implements IGCodeExecutionListener<ExecutionTokenState, ExecutionToken<ExecutionTokenState>>, IWorkspaceListener, IGCodeProviderRepositoryListener{
	private static final GkLog LOG = GkLog.getLogger(ExecutionPartController.class);
	/** The execution service */
	@Inject
	private IExecutionService<ExecutionTokenState, ExecutionToken<ExecutionTokenState>> executionService;
	/** The underlying workspace service */
	@Inject
	private IWorkspaceService workspaceService;
	/** The repository for GCode providers*/
	@Inject
	private IGCodeProviderRepository gcodeProviderRepository;
	/** The optional execution time evaluation service */
	@Inject
	@Optional
	private IGCodeExecutionTimeService executionTimeService;
	/** Job for execution time estimation */
	private Job executionUpdater;
	/** Map of execution time by token id */
	private Map<Integer, Time> executionTimeByToken;
	
	/** Constructor */
	public ExecutionPartController() {
		super(new ExecutionPartModel());
	}

	/** (inheritDoc)
	 * @see org.goko.common.bindings.AbstractController#initialize()
	 */
	@Override
	public void initialize() throws GkException {
		executionService.addExecutionListener(this);
		workspaceService.addWorkspaceListener(this);
		gcodeProviderRepository.addListener(this);
		updateTokenQueueData();
		updateButtonState();
		workspaceService.addWorkspaceListener(this);
		executionTimeByToken = new HashMap<Integer, Time>();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionStart(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionStart(ExecutionToken<ExecutionTokenState> token) throws GkException {
		this.getDataModel().setCompletedLineCount(0);
		this.getDataModel().setTokenLineCount(token.getLineCount());
		updateButtonState();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onQueueExecutionComplete()
	 */
	@Override
	public void onQueueExecutionComplete() throws GkException {
		updateButtonState();
		this.getDataModel().setExecutionTimerActive(false);
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onQueueExecutionStart()
	 */
	@Override
	public void onQueueExecutionStart() throws GkException {
		updateEstimatedExecutionTime();
		updateButtonState();
		this.getDataModel().setExecutionQueueStartDate(new Date());
		this.getDataModel().setExecutionTimerActive(true);
		updateQueueExecutionState();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionCanceled(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionCanceled(ExecutionToken<ExecutionTokenState> token) throws GkException {
		updateButtonState();
		updateQueueExecutionState();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onQueueExecutionCanceled()
	 */
	@Override
	public void onQueueExecutionCanceled() throws GkException {
		updateButtonState();
		updateQueueExecutionState();
		this.getDataModel().setExecutionTimerActive(false);
	}
	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionPause(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionPause(ExecutionToken<ExecutionTokenState> token) throws GkException {
		updateButtonState();
		updateQueueExecutionState();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionResume(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionResume(ExecutionToken<ExecutionTokenState> token) throws GkException {
		updateButtonState();
		updateQueueExecutionState();
	}
	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionComplete(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionComplete(ExecutionToken<ExecutionTokenState> token) throws GkException {
		updateTokenQueueData();
		updateButtonState();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeLineExecutionListener#onLineStateChanged(org.goko.core.gcode.execution.IExecutionToken, java.lang.Integer)
	 */
	@Override
	public void onLineStateChanged(ExecutionToken<ExecutionTokenState> token, Integer idLine) throws GkException {
		int executedLineCount = token.getLineCountByState(ExecutionTokenState.EXECUTED);
		this.getDataModel().setCompletedLineCount(executedLineCount);
	}

	/**
	 * @param event
	 * @throws GkException
	 */
	@Override
	public void onWorkspaceEvent(IWorkspaceEvent event) throws GkException {
		updateButtonState();
		if(StringUtils.equalsIgnoreCase(event.getType(), ExecutionServiceWorkspaceEvent.TYPE)){
			updateTokenQueueData();
		}
	}

	/**
	 * Starts the execution queue
	 * @throws GkException GkException
	 */
	public void beginQueueExecution() throws GkException {
		executionService.beginQueueExecution();
		this.getDataModel().setExecutionQueueStartDate(new Date());
		this.getDataModel().setExecutionTimerActive(true);
	}

	/**
	 * Pauses the execution queue
	 * @throws GkException GkException
	 */
	public void pauseResumeQueueExecution() throws GkException {
		if(executionService.getExecutionState() == ExecutionState.PAUSED){
			executionService.resumeQueueExecution();
		}else if(executionService.getExecutionState() == ExecutionState.RUNNING){
			executionService.pauseQueueExecution();
		}
	}

	/**
	 * Stops the execution queue
	 * @throws GkException GkException
	 */
	public void stopQueueExecution() throws GkException {
		executionService.stopQueueExecution();
	}

	/**
	 * Update the state of the buttons according to the execution state
	 * @throws GkException GkException
	 */
	private void updateButtonState() throws GkException{
		boolean buttonStopEnabled = false;
		boolean buttonStartEnabled= false;
		boolean buttonPauseEnabled = false;

		switch(executionService.getExecutionState()){
		case IDLE:
		case STOPPED:
		case COMPLETE:  buttonStartEnabled = true;
					buttonPauseEnabled = false;
					buttonStopEnabled = false;
				break;
		case PAUSED:
		case ERROR:
		case RUNNING: buttonStartEnabled = false;
					  buttonPauseEnabled = true;
					  buttonStopEnabled = true;
				break;
		}

		if(executionService.getExecutionQueue() == null
		|| CollectionUtils.isEmpty(executionService.getExecutionQueue().getExecutionToken())){
			buttonStartEnabled = false;
		}
		getDataModel().setButtonStartEnabled(buttonStartEnabled);
		getDataModel().setButtonPauseEnabled(buttonPauseEnabled);
		getDataModel().setButtonStopEnabled( buttonStopEnabled);
	}
	/**
	 * Update the data about the execution of the queue
	 * @throws GkException GkException
	 */
	private void updateQueueExecutionState() throws GkException{
		if(executionService.getExecutionState() == ExecutionState.RUNNING){
			getDataModel().setProgressBarState(SWT.NORMAL);
		}else if(executionService.getExecutionState() == ExecutionState.PAUSED){
			getDataModel().setProgressBarState(SWT.PAUSED);
		}else if(executionService.getExecutionState() == ExecutionState.STOPPED){
			getDataModel().setProgressBarState(SWT.ERROR);
		}
	}
	/**
	 * Update the estimated execution time
	 * @throws GkException GkException
	 */
	private void updateEstimatedExecutionTime() throws GkException{
		if(executionUpdater == null || executionUpdater.getState() == Job.NONE){
			scheduleExecutionTimeEstimationJob();
		}
	}

	private void scheduleExecutionTimeEstimationJob() throws GkException{
		scheduleExecutionTimeEstimationJob(null);
	}
	
	private void scheduleExecutionTimeEstimationJob(final Integer idToken) throws GkException{
		executionUpdater = new Job("Updating execution time..."){
			/** (inheritDoc)
			 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
			 */
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if(executionTimeService != null){
					try {
						List<ExecutionToken<ExecutionTokenState>> lstToken = executionService.getExecutionQueue().getExecutionToken();
						Time estimatedTime = Time.ZERO;
						if(CollectionUtils.isNotEmpty(lstToken)){
							SubMonitor subMonitor = SubMonitor.convert(monitor,"Computing time...", lstToken.size());
							// Refresh the unitary times first
							for (ExecutionToken<ExecutionTokenState> executionToken : lstToken) {
								Time tokenTime = executionTimeByToken.get(executionToken.getId());
								if(tokenTime == null || idToken == null ||ObjectUtils.equals(idToken, executionToken.getId())){
									tokenTime = executionTimeService.evaluateExecutionTime(executionToken.getGCodeProvider());
									executionTimeByToken.put(executionToken.getId(), tokenTime);
								}
								subMonitor.worked(1);
								estimatedTime = estimatedTime.add(tokenTime);
							}							
							subMonitor.done();
						}
						long estimatedMs = (long)estimatedTime.doubleValue(TimeUnit.MILLISECOND);
						ExecutionPartController.this.getDataModel().setEstimatedTimeString(DurationFormatUtils.formatDuration(estimatedMs, "HH:mm:ss"));
					} catch (GkException e) {
						LOG.error(e);
					}
				}
				return Status.OK_STATUS;
			}
		};
		executionUpdater.setUser(true);
		executionUpdater.schedule(100);
	}

	/**
	 * Update the data about the execution queue
	 * @throws GkException GkException
	 */
	private void updateTokenQueueData() throws GkException {
		List<ExecutionToken<ExecutionTokenState>> lstToken = executionService.getExecutionQueue().getExecutionToken();
		int tokenCount = CollectionUtils.size(lstToken);
		this.getDataModel().setTotalTokenCount(tokenCount);

		int totalTokenCount 	= 0;
		int completedTokenCount = 0;
		int totalLineCount		= 0;

		for (ExecutionToken<ExecutionTokenState> executionToken : lstToken) {
			totalTokenCount += 1;
			totalLineCount += executionToken.getLineCount();
			if(executionToken.getState() == ExecutionState.COMPLETE){
				completedTokenCount += 1;
			}
		}
		this.getDataModel().setCompletedTokenCount(completedTokenCount);
		this.getDataModel().setTotalTokenCount(totalTokenCount);
		this.getDataModel().setTotalLineCount(totalLineCount);
		updateEstimatedExecutionTime();
	}

	/**
	 * @return the executionService
	 */
	public IExecutionService<ExecutionTokenState, ExecutionToken<ExecutionTokenState>> getExecutionService() {
		return executionService;
	}

	private Integer getExecutionTokenByGCodeProvider(Integer idGCodeProvider) throws GkException{
		List<ExecutionToken<ExecutionTokenState>> lstToken = executionService.getExecutionQueue().getExecutionToken();
		
		if(CollectionUtils.isNotEmpty(lstToken)){			
			// Refresh the unitary times first
			for (ExecutionToken<ExecutionTokenState> executionToken : lstToken) {				
				if(ObjectUtils.equals(idGCodeProvider, executionToken.getIdGCodeProvider())){
					return executionToken.getId();
				}
			}	
		}
		return null;
	}
	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeProviderRepositoryListener#onGCodeProviderCreate(org.goko.core.gcode.element.IGCodeProvider)
	 */
	@Override
	public void onGCodeProviderCreate(IGCodeProvider provider) throws GkException {}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeProviderRepositoryListener#onGCodeProviderUpdate(org.goko.core.gcode.element.IGCodeProvider)
	 */
	@Override
	public void onGCodeProviderUpdate(IGCodeProvider provider) throws GkException {
		Integer idToken = getExecutionTokenByGCodeProvider(provider.getId());
		if(idToken != null){
			scheduleExecutionTimeEstimationJob( idToken );
		}
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeProviderRepositoryListener#onGCodeProviderDelete(org.goko.core.gcode.element.IGCodeProvider)
	 */
	@Override
	public void onGCodeProviderDelete(IGCodeProvider provider) throws GkException {
		Integer idToken = getExecutionTokenByGCodeProvider(provider.getId());
		if(idToken != null){
			executionTimeByToken.remove(idToken);
			scheduleExecutionTimeEstimationJob(idToken);
		}
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeProviderRepositoryListener#onGCodeProviderLocked(org.goko.core.gcode.element.IGCodeProvider)
	 */
	@Override
	public void onGCodeProviderLocked(IGCodeProvider provider) throws GkException {}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeProviderRepositoryListener#onGCodeProviderUnlocked(org.goko.core.gcode.element.IGCodeProvider)
	 */
	@Override
	public void onGCodeProviderUnlocked(IGCodeProvider provider) throws GkException {}

}
