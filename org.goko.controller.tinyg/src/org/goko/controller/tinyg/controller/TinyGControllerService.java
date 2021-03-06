package org.goko.controller.tinyg.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.goko.controller.tinyg.controller.bean.TinyGExecutionError;
import org.goko.controller.tinyg.controller.configuration.TinyGAxisSettings;
import org.goko.controller.tinyg.controller.configuration.TinyGConfiguration;
import org.goko.controller.tinyg.controller.configuration.TinyGConfigurationValue;
import org.goko.controller.tinyg.controller.configuration.TinyGGroupSettings;
import org.goko.controller.tinyg.controller.prefs.TinyGPreferences;
import org.goko.controller.tinyg.controller.probe.ProbeCallable;
import org.goko.controller.tinyg.controller.topic.TinyGExecutionErrorTopic;
import org.goko.controller.tinyg.json.TinyGJsonUtils;
import org.goko.controller.tinyg.service.ITinyGControllerFirmwareService;
import org.goko.core.common.GkUtils;
import org.goko.core.common.applicative.logging.ApplicativeLogEvent;
import org.goko.core.common.applicative.logging.IApplicativeLogService;
import org.goko.core.common.event.EventBrokerUtils;
import org.goko.core.common.event.EventDispatcher;
import org.goko.core.common.event.EventListener;
import org.goko.core.common.event.ObservableDelegate;
import org.goko.core.common.exception.GkException;
import org.goko.core.common.exception.GkFunctionalException;
import org.goko.core.common.exception.GkTechnicalException;
import org.goko.core.common.measure.Units;
import org.goko.core.common.measure.quantity.Angle;
import org.goko.core.common.measure.quantity.AngleUnit;
import org.goko.core.common.measure.quantity.Length;
import org.goko.core.common.measure.quantity.Speed;
import org.goko.core.common.measure.units.Unit;
import org.goko.core.config.GokoPreference;
import org.goko.core.connection.IConnectionService;
import org.goko.core.connection.serial.ISerialConnection;
import org.goko.core.connection.serial.ISerialConnectionService;
import org.goko.core.connection.serial.SerialParameter;
import org.goko.core.controller.action.IGkControllerAction;
import org.goko.core.controller.bean.EnumControllerAxis;
import org.goko.core.controller.bean.MachineState;
import org.goko.core.controller.bean.MachineValue;
import org.goko.core.controller.bean.MachineValueDefinition;
import org.goko.core.controller.bean.ProbeRequest;
import org.goko.core.controller.bean.ProbeResult;
import org.goko.core.controller.event.IGCodeContextListener;
import org.goko.core.controller.event.MachineValueUpdateEvent;
import org.goko.core.gcode.element.GCodeLine;
import org.goko.core.gcode.element.ICoordinateSystem;
import org.goko.core.gcode.element.IGCodeProvider;
import org.goko.core.gcode.execution.ExecutionState;
import org.goko.core.gcode.execution.ExecutionTokenState;
import org.goko.core.gcode.execution.IExecutionToken;
import org.goko.core.gcode.rs274ngcv3.IRS274NGCService;
import org.goko.core.gcode.rs274ngcv3.context.EnumCoordinateSystem;
import org.goko.core.gcode.rs274ngcv3.context.EnumDistanceMode;
import org.goko.core.gcode.rs274ngcv3.context.GCodeContext;
import org.goko.core.gcode.rs274ngcv3.context.GCodeContextObservable;
import org.goko.core.gcode.rs274ngcv3.element.InstructionProvider;
import org.goko.core.gcode.rs274ngcv3.instruction.SetDistanceModeInstruction;
import org.goko.core.gcode.rs274ngcv3.instruction.SetFeedRateInstruction;
import org.goko.core.gcode.rs274ngcv3.instruction.StraightFeedInstruction;
import org.goko.core.gcode.rs274ngcv3.instruction.StraightProbeInstruction;
import org.goko.core.gcode.service.IExecutionService;
import org.goko.core.log.GkLog;
import org.goko.core.math.Tuple6b;
import org.osgi.service.event.EventAdmin;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

/**
 * Implementation of the TinyG controller
 *
 * @author PsyKo
 *
 */
public class TinyGControllerService extends EventDispatcher implements ITinyGControllerFirmwareService, ITinygControllerService{
	static final GkLog LOG = GkLog.getLogger(TinyGControllerService.class);
	/**  Service ID */
	public static final String SERVICE_ID = "Controller for TinyG v0.97";
	/** Stored configuration */
	private TinyGConfiguration configuration;
	/** Connection service */
	private ISerialConnectionService connectionService;
	/** GCode service */
	private IRS274NGCService gcodeService;
	/** The monitor service */
	private IExecutionService<ExecutionTokenState, IExecutionToken<ExecutionTokenState>> executionService;			
	/** Action factory */
	private TinyGActionFactory actionFactory;
	/** Storage object for machine values (speed, position, etc...) */
	private TinyGState tinygState;
	/** Communicator */
	private TinyGCommunicator communicator;
	/** Applicative log service */
	private IApplicativeLogService applicativeLogService;
	/** Event admin service */
	private EventAdmin eventAdmin;
	//private TinyGJoggingRunnable jogRunnable;
	private TinyGJogging tinyGJogging;
	private TinyGExecutor tinygExecutor;
	private CompletionService<ProbeResult> completionService;
	private List<ProbeCallable> lstProbeCallable;	
	private IGCodeProvider probeGCodeProvider;
	private ObservableDelegate<IGCodeContextListener<GCodeContext>> gcodeContextListener;
	/**
	 * Constructor
	 * @throws GkException GkException
	 */
	public TinyGControllerService() throws GkException {
		communicator = new TinyGCommunicator(this);
		tinygState   = new TinyGState();
		tinygExecutor = new TinyGExecutor(this, null);		
		gcodeContextListener = new GCodeContextObservable();
	}

	/** (inheritDoc)
	 * @see org.goko.core.common.service.IGokoService#getServiceId()
	 */
	@Override
	public String getServiceId() throws GkException {
		return SERVICE_ID;
	}

	/** (inheritDoc)
	 * @see org.goko.core.common.service.IGokoService#start()
	 */
	@Override
	public void start() throws GkException {
		LOG.info("Starting "+getServiceId());
		configuration 			= new TinyGConfiguration();
		actionFactory 			= new TinyGActionFactory(this);

		tinygState.addListener(this);

		TinyGPreferences.getInstance();

//		jogRunnable = new TinyGJoggingRunnable(this, communicator);
//		ExecutorService jogExecutor 	= Executors.newSingleThreadExecutor();
//		jogExecutor.execute(jogRunnable);
		executionService.addExecutionListener(this);
		tinyGJogging = new TinyGJogging(this, communicator);
		LOG.info("Successfully started "+getServiceId());
	}

	/** (inheritDoc)
	 * @see org.goko.core.common.service.IGokoService#stop()
	 */
	@Override
	public void stop() throws GkException {
//		if(currentSendingRunnable != null){
//			currentSendingRunnable.stop();
//		}
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getPosition()
	 */
	@Override
	public Tuple6b getPosition() throws GkException {
		return tinygState.getWorkPosition();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#verifyReadyForExecution()
	 */
	@Override
	public void verifyReadyForExecution() throws GkException{
		if(!isReadyForFileStreaming()){
			throw new GkFunctionalException("TNG-003");
		}
		updateQueueReport();
		checkExecutionControl();
		checkVerbosity(configuration);
	}

	private void checkVerbosity(TinyGConfiguration cfg) throws GkException{
		BigDecimal jsonVerbosity = cfg.getSetting(TinyGConfiguration.SYSTEM_SETTINGS, TinyGConfiguration.JSON_VERBOSITY, BigDecimal.class);
		if(!ObjectUtils.equals(jsonVerbosity, TinyGConfigurationValue.JSON_VERBOSITY_VERBOSE)){
			throw new GkFunctionalException("TNG-007");
		}

		BigDecimal qrVerbosity = cfg.getSetting(TinyGConfiguration.SYSTEM_SETTINGS, TinyGConfiguration.QUEUE_REPORT_VERBOSITY, BigDecimal.class);

		if(isPlannerBufferSpaceCheck()){
			if(ObjectUtils.equals(qrVerbosity, TinyGConfigurationValue.QUEUE_REPORT_OFF)){
				throw new GkFunctionalException("TNG-002");
			}
		}
	}

	private void checkExecutionControl() throws GkException{
		BigDecimal flowControl = configuration.getSetting(TinyGConfiguration.SYSTEM_SETTINGS, TinyGConfiguration.ENABLE_FLOW_CONTROL, BigDecimal.class);

		// We always need to use flow control
		if(ObjectUtils.equals(flowControl, TinyGConfigurationValue.FLOW_CONTROL_OFF)){
			throw new GkFunctionalException("TNG-001");
		}

		// Make sure the current connection use the same flow control
		ISerialConnection connexion = getConnectionService().getCurrentConnection();
		TinyGConfiguration cfg = getConfiguration();
		BigDecimal configuredFlowControl = cfg.getSetting(TinyGConfiguration.SYSTEM_SETTINGS, TinyGConfiguration.ENABLE_FLOW_CONTROL, BigDecimal.class);

		if(configuredFlowControl.equals(TinyGConfigurationValue.FLOW_CONTROL_RTS_CTS)){ // TinyG expects RtsCts but the serial connection does not use it
			if((connexion.getFlowControl() & SerialParameter.FLOWCONTROL_RTSCTS) != SerialParameter.FLOWCONTROL_RTSCTS){
				throw new GkFunctionalException("TNG-005");
			}
		}else if(configuredFlowControl.equals(TinyGConfigurationValue.FLOW_CONTROL_XON_XOFF)){ // TinyG expects XonXoff but the serial connection does not use it
			if((connexion.getFlowControl() & SerialParameter.FLOWCONTROL_XONXOFF) != SerialParameter.FLOWCONTROL_XONXOFF){
				throw new GkFunctionalException("TNG-006");
			}
		}
	}

	/** (inheritDoc)
	 * @see org.goko.controller.tinyg.controller.ITinygControllerService#send(org.goko.core.gcode.element.GCodeLine)
	 */
	@Override
	public void send(GCodeLine gCodeLine) throws GkException{
		String gcodeString = gcodeService.render(gCodeLine);
		if(StringUtils.isNotBlank(gcodeString)){
			JsonValue jsonStr = TinyGControllerUtility.toJson(gcodeString);
			communicator.send(GkUtils.toBytesList(jsonStr.toString()));
		}else{
			tinygExecutor.confirmNextLineExecution();
		}
	}

	public void sendTogether(List<GCodeLine> gCodeLines) throws GkException{
		for (GCodeLine gCodeLine : gCodeLines) {
			send(gCodeLine);
		}
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#isReadyForFileStreaming()
	 */
	@Override
	public boolean isReadyForFileStreaming() throws GkException {
		return MachineState.READY.equals(getState())
			|| MachineState.PROGRAM_END.equals(getState())
			|| MachineState.PROGRAM_STOP.equals(getState());
	}
	
	/** (inheritDoc)
	 * @see org.goko.core.controller.IProbingService#checkReadyToProbe()
	 */
	@Override
	public void checkReadyToProbe() throws GkException {
		if(!connectionService.isConnected()){
			throw new GkFunctionalException("TNG-008");
		}
		if(!isReadyForFileStreaming()){
			throw new GkFunctionalException("TNG-003");
		}
	}
	
	/** (inheritDoc)
	 * @see org.goko.core.controller.IProbingService#isReadyToProbe()
	 */
	@Override
	public boolean isReadyToProbe() {		
		try {
			return connectionService.isConnected() && isReadyForFileStreaming();
		} catch (GkException e) {
			LOG.error(e);
			return false;
		}
	}
	/**
	 * Refresh the TinyG configuration by sending all the Groups as empty groups
	 * Update is done by event handling
	 *
	 * @throws GkException GkException
	 */
	@Override
	public void refreshConfiguration() throws GkException{
		for(TinyGGroupSettings group : configuration.getGroups()){
			JsonObject groupEmpty = new JsonObject();
			groupEmpty.add(group.getGroupIdentifier(), StringUtils.EMPTY);
			communicator.send(GkUtils.toBytesList(groupEmpty.toString()));
		}
	}
	public void refreshStatus() throws GkException{
		JsonObject statusQuery = new JsonObject();
		statusQuery.add("sr", StringUtils.EMPTY);
		communicator.send(GkUtils.toBytesList(statusQuery.toString()));
		updateQueueReport();
	}

	protected void updateQueueReport()throws GkException{
		JsonObject queueQuery = new JsonObject();
		queueQuery.add("qr", StringUtils.EMPTY);
		communicator.send(GkUtils.toBytesList(queueQuery.toString()));
	}

	/**
	 * Update the current GCodeContext with the given one
	 * @param updatedGCodeContext the updated GCodeContext
	 * @throws GkException GkException
	 */
	protected void updateCurrentGCodeContext(GCodeContext updatedGCodeContext) throws GkException{
		GCodeContext current = tinygState.getGCodeContext();
		if(updatedGCodeContext.getPosition() != null){
			current.setPosition(updatedGCodeContext.getPosition());
		}
		if(updatedGCodeContext.getCoordinateSystem() != null){
			current.setCoordinateSystem(updatedGCodeContext.getCoordinateSystem());
		}
		if(updatedGCodeContext.getMotionMode() != null){
			current.setMotionMode(updatedGCodeContext.getMotionMode());
		}
//		if(updatedGCodeContext.getMotionType() != null){
//			current.setMotionType(updatedGCodeContext.getMotionType());
//		}
		if(updatedGCodeContext.getFeedrate() != null){
			current.setFeedrate(updatedGCodeContext.getFeedrate());
		}
		if(updatedGCodeContext.getUnit() != null){
			current.setUnit(updatedGCodeContext.getUnit());
		}
		if(updatedGCodeContext.getDistanceMode() != null){
			current.setDistanceMode(updatedGCodeContext.getDistanceMode());
		}
		if(updatedGCodeContext.getPlane() != null){
			current.setPlane(updatedGCodeContext.getPlane());
		}
		if(updatedGCodeContext.getActiveToolNumber() != null){
			current.setActiveToolNumber(updatedGCodeContext.getActiveToolNumber());
		}
		if(updatedGCodeContext.getSelectedToolNumber() != null){
			current.setSelectedToolNumber(updatedGCodeContext.getSelectedToolNumber());
		}
		tinygState.setGCodeContext(current);
		gcodeContextListener.getEventDispatcher().onGCodeContextEvent(current);
	}

	/** (inheritDoc)
	 * @see org.goko.core.common.event.IObservable#addObserver(java.lang.Object)
	 */
	@Override
	public void addObserver(IGCodeContextListener<GCodeContext> observer) {
		gcodeContextListener.addObserver(observer);
	}
	
	/** (inheritDoc)
	 * @see org.goko.core.common.event.IObservable#removeObserver(java.lang.Object)
	 */
	@Override
	public boolean removeObserver(IGCodeContextListener<GCodeContext> observer) {		
		return gcodeContextListener.removeObserver(observer);
	}
	
	/**
	 * Return a copy of the current stored configuration
	 * @return a copy of {@link TinyGConfiguration}
	 * @throws GkException GkException
	 */
	@Override
	public TinyGConfiguration getConfiguration() throws GkException{
		return TinyGControllerUtility.getConfigurationCopy(configuration);
	}

	/**
	 * Returned the available {@link IConnectionService}
	 * @return the connectionService
	 */
	public ISerialConnectionService getConnectionService() {
		return connectionService;
	}

	/**
	 * @param connectionService the connectionService to set
	 * @throws GkException GkException
	 */
	public void setConnectionService(ISerialConnectionService connectionService) throws GkException {
		this.connectionService = connectionService;
		communicator.setConnectionService(connectionService);
	}

	/**
	 * Handling GCode response from TinyG
	 * @param jsonValue
	 * @throws GkTechnicalException
	 */
	protected void handleGCodeResponse(String receivedCommand, TinyGStatusCode status) throws GkException {
		if(executionService.getExecutionState() == ExecutionState.RUNNING){
			if(status == TinyGStatusCode.TG_OK){
				tinygExecutor.confirmNextLineExecution();
			}else{
				notifyNonOkStatus(status, receivedCommand);
				tinygExecutor.handleNonOkStatus(status);
			}
		}else{
			if(status != TinyGStatusCode.TG_OK){
				notifyNonOkStatus(status, receivedCommand);
			}
		}
	}

	/**
	 * Handles any TinyG Status that is not TG_OK
	 * @param status the received status or <code>null</code> if unknown
	 * @param receivedCommand the received command
	 * @throws GkException GkException
	 */
	protected void notifyNonOkStatus(TinyGStatusCode status, String receivedCommand) throws GkException {
		String message = StringUtils.EMPTY;

		if(status == null){
			message = " Unknown error status";
		}else{
			if(status.isError()){
				// Error report
				message = "Error status returned : "+status.getValue() +" - "+status.getLabel();
				LOG.error(message);
				applicativeLogService.log(ApplicativeLogEvent.LOG_ERROR, message, "TinyG");
				EventBrokerUtils.send(eventAdmin, new TinyGExecutionErrorTopic(), new TinyGExecutionError("Error reported durring execution", "Execution was paused after TinyG reported an error. You can resume, or stop the execution at your own risk.", message));
			}else if(status.isWarning()){
				// Warning report
				message = "Warning status returned : "+status.getValue() +" - "+status.getLabel();
				LOG.warn(message);
				applicativeLogService.log(ApplicativeLogEvent.LOG_WARNING, message, "TinyG");
			}
		}
	}


	@EventListener(MachineValueUpdateEvent.class)
	public void onMachineValueUpdate(MachineValueUpdateEvent evt){
		notifyListeners(evt);
	}

	/** (inheritDoc)
	 * @see org.goko.controller.tinyg.service.ITinyGControllerFirmwareService#setConfiguration(org.goko.controller.tinyg.controller.configuration.TinyGConfiguration)
	 */
	@Override
	public void setConfiguration(TinyGConfiguration cfg) throws GkException{
		this.configuration = TinyGControllerUtility.getConfigurationCopy(cfg);
	}

	/** (inheritDoc)
	 * @see org.goko.controller.tinyg.service.ITinyGControllerFirmwareService#updateConfiguration(org.goko.controller.tinyg.controller.configuration.TinyGConfiguration)
	 */
	@Override
	public void updateConfiguration(TinyGConfiguration cfg) throws GkException {
		checkVerbosity(cfg);
		// Let's only change the new values
		// The new value will be applied directly to TinyG. The changes will be reported in the data model when TinyG sends them back for confirmation.
		TinyGConfiguration diffConfig = TinyGControllerUtility.getDifferentialConfiguration(getConfiguration(), cfg);

		// TODO : perform sending in communicator
		for(TinyGGroupSettings group: diffConfig.getGroups()){
			JsonObject jsonGroup = TinyGJsonUtils.toCompleteJson(group);
			if(jsonGroup != null){
				communicator.send( GkUtils.toBytesList(jsonGroup.toString()) );
			}
		}
	}


	/**
	 * @return the gcodeService
	 */
	public IRS274NGCService getGcodeService() {
		return gcodeService;
	}

	/**
	 * @param gCodeService the gcodeService to set
	 */
	public void setGCodeService(IRS274NGCService gCodeService) {
		this.gcodeService = gCodeService;
		this.communicator.setGcodeService(gCodeService);
		this.tinygExecutor.setRs274Service(gCodeService);
	}

	@Override
	public MachineState getState() throws GkException {
		return tinygState.getState();
	}

	public void setState(MachineState state) throws GkException {
		tinygState.setState(state);		
	}

	public boolean isSpindleOn() throws GkException{
		return tinygState.isSpindleOn();
	}

	public boolean isSpindleOff() throws GkException{
		return tinygState.isSpindleOff();
	}

	/**
	 * Initiate TinyG homing sequence
	 * @throws GkException GkException
	 */
	public void startHomingSequence() throws GkException{
		LOG.info("Homing...");
		String 		homingCommand 		= "G28.2";
		if(TinyGPreferences.getInstance().isHomingEnabledAxisX()){
			homingCommand += " X0";
		}
		if(TinyGPreferences.getInstance().isHomingEnabledAxisY()){
			homingCommand += " Y0";
		}
		if(TinyGPreferences.getInstance().isHomingEnabledAxisZ()){
			homingCommand += " Z0";
		}
		if(TinyGPreferences.getInstance().isHomingEnabledAxisA()){
			homingCommand += " A0";
		}
		communicator.send(GkUtils.toBytesList(homingCommand));
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getControllerAction(java.lang.String)
	 */
	@Override
	public IGkControllerAction getControllerAction(String actionId) throws GkException {
		IGkControllerAction action = actionFactory.findAction(actionId);
		if(action == null){
			throw new GkFunctionalException("TNG-004", actionId, getServiceId());
		}
		return action;
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#isControllerAction(java.lang.String)
	 */
	@Override
	public boolean isControllerAction(String actionId) throws GkException {
		return actionFactory.findAction(actionId) != null;
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getMachineValue(java.lang.String, java.lang.Class)
	 */
	@Override
	public <T> MachineValue<T> getMachineValue(String name, Class<T> clazz) throws GkException {
		return tinygState.getValue(name, clazz);
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getMachineValueType(java.lang.String)
	 */
	@Override
	public Class<?> getMachineValueType(String name) throws GkException {
		return tinygState.getControllerValueType(name);
	}

	@Override
	public List<MachineValueDefinition> getMachineValueDefinition() throws GkException {
		return tinygState.getMachineValueDefinition();
	}
	@Override
	public MachineValueDefinition getMachineValueDefinition(String id) throws GkException {
		return tinygState.getMachineValueDefinition(id);
	}
	@Override
	public MachineValueDefinition findMachineValueDefinition(String id) throws GkException {
		return tinygState.findMachineValueDefinition(id);
	}


	/* ************************************************
	 *  CONTROLLER ACTIONS
	 * ************************************************/


	/** (inheritDoc)
	 * @see org.goko.controller.tinyg.controller.ITinygControllerService#pauseMotion()
	 */
	@Override
	public void pauseMotion() throws GkException{
		communicator.send(GkUtils.toBytesList(TinyG.FEED_HOLD));
		executionService.pauseQueueExecution();
	}
	/** (inheritDoc)
	 * @see org.goko.controller.tinyg.controller.ITinygControllerService#resumeMotion()
	 */
	@Override
	public void resumeMotion() throws GkException{
		communicator.sendImmediately(GkUtils.toBytesList(TinyG.CYCLE_START));
		executionService.resumeQueueExecution();
	}

	/** (inheritDoc)
	 * @see org.goko.controller.tinyg.controller.ITinygControllerService#startMotion()
	 */
	@Override
	public void startMotion() throws GkException {
		communicator.sendImmediately(GkUtils.toBytesList(TinyG.CYCLE_START));
		executionService.beginQueueExecution();
	}
	/** (inheritDoc)
	 * @see org.goko.controller.tinyg.controller.ITinygControllerService#stopMotion()
	 */
	@Override
	public void stopMotion() throws GkException{
		getConnectionService().clearOutputBuffer();
		
		if(CollectionUtils.isNotEmpty(lstProbeCallable)){
			// Probe active, let's hack over a TinyG bug
			communicator.sendImmediately(GkUtils.toBytesList(TinyG.FEED_HOLD));
			Executors.newSingleThreadExecutor().submit(new Runnable() {
				
				@Override
				public void run() {					
					try {
						Thread.sleep(1000);
						communicator.sendImmediately(GkUtils.toBytesList(TinyG.QUEUE_FLUSH));
					} catch (GkException | InterruptedException e) {
						LOG.error(e);
					}
				}
			});
		}else{
			communicator.sendImmediately(GkUtils.toBytesList(TinyG.FEED_HOLD, TinyG.QUEUE_FLUSH));
			communicator.sendImmediately(GkUtils.toBytesList("G90"));
		}
				
		executionService.stopQueueExecution();
		cancelActiveProbing();
		//communicator.send(GkUtils.toBytesList(TinyG.QUEUE_FLUSH));
		// Force a queue report update
		//	updateQueueReport();
		//	this.resetAvailableBuffer();
	}

	public void resetZero(List<String> axes) throws GkException{
		List<Byte> lstBytes = GkUtils.toBytesList("G28.3");
		if(CollectionUtils.isNotEmpty(axes)){
			for (String axe : axes) {
				lstBytes.addAll(GkUtils.toBytesList(axe+"0"));
			}
		}else{
			lstBytes.addAll( GkUtils.toBytesList("X0Y0Z0"));
		}
		communicator.send(lstBytes);
	}

	public void turnSpindleOn() throws GkException{
		communicator.send(GkUtils.toBytesList("M3"));
	}

	public void turnSpindleOff() throws GkException{
		communicator.send(GkUtils.toBytesList("M5"));
	}

	/**
	 * @return the availableBuffer
	 * @throws GkException
	 */
	@Override
	public int getAvailableBuffer() throws GkException {
		return tinygState.getAvailableBuffer();
	}
	/**
	 * @param availableBuffer the availableBuffer to set
	 * @throws GkException exception
	 */
	@Override
	public void setAvailableBuffer(int availableBuffer) throws GkException {
		tinygState.updateValue(TinyG.TINYG_BUFFER_COUNT, availableBuffer);
		tinygExecutor.onBufferSpaceAvailableChange(availableBuffer);
	}

	public void resetAvailableBuffer() throws GkException {
		setAvailableBuffer(28);
	}

	@Override
	public void cancelFileSending() throws GkException {
		stopMotion();
	}


	@Override
	public String getMinimalSupportedFirmwareVersion() throws GkException {
		return "435.10";
	}

	@Override
	public String getMaximalSupportedFirmwareVersion() throws GkException {
		return "440.18";
	}

	protected void cancelActiveProbing() throws GkException{
		if(CollectionUtils.isNotEmpty(lstProbeCallable)){
			for (ProbeCallable probeCallable: lstProbeCallable) {
				probeCallable.setProbeResult(null);
			}
			lstProbeCallable.clear();			
		}
	}
	
	protected void clearProbingGCode() throws GkException{
		if(probeGCodeProvider != null){
			gcodeService.deleteGCodeProvider(probeGCodeProvider.getId());
			probeGCodeProvider = null;
		}
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IProbingService#probe(java.util.List)
	 */
	@Override
	public CompletionService<ProbeResult> probe(List<ProbeRequest> lstProbeRequest) throws GkException {		
		Executor executor = Executors.newSingleThreadExecutor();
		this.completionService = new ExecutorCompletionService<ProbeResult>(executor);		
		this.lstProbeCallable = new ArrayList<>();
		
		for (ProbeRequest probeRequest : lstProbeRequest) {
			ProbeCallable probeCallable = new ProbeCallable();
			this.lstProbeCallable.add(probeCallable);
			completionService.submit(probeCallable);			
		}
		
		probeGCodeProvider = getZProbingCode(lstProbeRequest, getGCodeContext());
		probeGCodeProvider.setCode("TinyG probing");
		gcodeService.addGCodeProvider(probeGCodeProvider);
		probeGCodeProvider = gcodeService.getGCodeProvider(probeGCodeProvider.getId());// Required since internally the provider is a new one
		executionService.addToExecutionQueue(probeGCodeProvider);
		executionService.beginQueueExecution();
		
		return completionService;
	}
	
	/** (inheritDoc)
	 * @see org.goko.core.controller.IProbingService#probe(org.goko.core.controller.bean.EnumControllerAxis, double, double)
	 */
	@Override
	public CompletionService<ProbeResult> probe(ProbeRequest probeRequest) throws GkException {
		List<ProbeRequest> lstProbeRequest = new ArrayList<ProbeRequest>();
		lstProbeRequest.add(probeRequest);
		return probe(lstProbeRequest);		
	}

	private IGCodeProvider getZProbingCode(List<ProbeRequest> lstProbeRequest, GCodeContext gcodeContext) throws GkException{		
		InstructionProvider instrProvider = new InstructionProvider();
		// Force distance mode to absolute
		instrProvider.addInstruction( new SetDistanceModeInstruction(EnumDistanceMode.ABSOLUTE) );
		for (ProbeRequest probeRequest : lstProbeRequest) {
			// Move to clearance coordinate 
			instrProvider.addInstruction( new SetFeedRateInstruction(probeRequest.getMotionFeedrate()) );
			instrProvider.addInstruction( new StraightFeedInstruction(null, null, probeRequest.getClearance(), null, null, null) );
			// Move to probe position		
			instrProvider.addInstruction( new StraightFeedInstruction(probeRequest.getProbeCoordinate().getX(), probeRequest.getProbeCoordinate().getY(), null, null, null, null) );
			// Move to probe start position
			instrProvider.addInstruction( new StraightFeedInstruction(null, null, probeRequest.getProbeStart(), null, null, null) );
			// Actual probe command
			instrProvider.addInstruction( new SetFeedRateInstruction(probeRequest.getProbeFeedrate()) );
			instrProvider.addInstruction( new StraightProbeInstruction(null, null, probeRequest.getProbeEnd(), null, null, null) );
			// Move to clearance coordinate 
			instrProvider.addInstruction( new SetFeedRateInstruction(probeRequest.getMotionFeedrate()) );
			instrProvider.addInstruction( new StraightFeedInstruction(null, null, probeRequest.getClearance(), null, null, null) );
		}		
		return gcodeService.getGCodeProvider(gcodeContext, instrProvider);
	}
	
	protected void handleProbeResult(boolean probed, Tuple6b position) throws GkException{
		if(CollectionUtils.isNotEmpty(lstProbeCallable)){
			ProbeResult probeResult = new ProbeResult();
			probeResult.setProbed(probed);
			probeResult.setProbedPosition(position);
			
			ProbeCallable callable = lstProbeCallable.remove(0);
			callable.setProbeResult(probeResult);
		}		
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#moveToAbsolutePosition(org.goko.core.math.Tuple6b)
	 */
	@Override
	public void moveToAbsolutePosition(Tuple6b position) throws GkException {
		String cmd = "G1F800";
		if(position.getX() != null){
			cmd += "X"+getPositionAsString(position.getX());
		}
		if(position.getY() != null){
			cmd += "Y"+getPositionAsString(position.getY());
		}
		if(position.getZ() != null){
			cmd += "Z"+getPositionAsString(position.getZ());
		}
		IGCodeProvider command = gcodeService.parse(cmd);
		//executeGCode(command);
		throw new GkTechnicalException("To implement or remove");
	}

	protected String getPositionAsString(Length q) throws GkException{
		return GokoPreference.getInstance().format(q.to(getCurrentUnit()), true, false);
	}
	/**
	 * @param applicativeLogService the IApplicativeLogService to set
	 */
	public void setApplicativeLogService(IApplicativeLogService applicativeLogService) {
		this.applicativeLogService = applicativeLogService;
		this.communicator.setApplicativeLogService(applicativeLogService);
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerService#getGCodeContext()
	 */
	@Override
	public GCodeContext getGCodeContext() throws GkException {
		return tinygState.getGCodeContext();
	}
	
	public void setVelocity(Speed velocity) throws GkException {
		tinygState.setVelocity(velocity);
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IThreeAxisControllerAdapter#getX()
	 */
	@Override
	public Length getX() throws GkException {
		return tinygState.getX();
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IThreeAxisControllerAdapter#getY()
	 */
	@Override
	public Length getY() throws GkException {
		return tinygState.getY();
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IThreeAxisControllerAdapter#getZ()
	 */
	@Override
	public Length getZ() throws GkException {
		return tinygState.getZ();
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IFourAxisControllerAdapter#getA()
	 */
	@Override
	public Angle getA() throws GkException {
		return tinygState.getA();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#getCoordinateSystemOffset(org.goko.core.gcode.bean.commands.EnumCoordinateSystem)
	 */
	@Override
	public Tuple6b getCoordinateSystemOffset(ICoordinateSystem cs) throws GkException {
		return tinygState.getCoordinateSystemOffset(cs);
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#getCurrentCoordinateSystem()
	 */
	@Override
	public ICoordinateSystem getCurrentCoordinateSystem() throws GkException {
		return tinygState.getGCodeContext().getCoordinateSystem();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#getCoordinateSystem()
	 */
	@Override
	public List<ICoordinateSystem> getCoordinateSystem() throws GkException {
		List<ICoordinateSystem> lstCoordinateSystem = new ArrayList<ICoordinateSystem>();
		lstCoordinateSystem.addAll(Arrays.asList(EnumCoordinateSystem.values()));
		return lstCoordinateSystem;
	}
	public void setCoordinateSystemOffset(EnumCoordinateSystem cs, Tuple6b offset) throws GkException {
		tinygState.setCoordinateSystemOffset(cs, offset);
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IContinuousJogService#stopJog()
	 */
	@Override
	public void stopJog() throws GkException {
		tinyGJogging.stopJog();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#setCurrentCoordinateSystem(org.goko.core.gcode.bean.commands.EnumCoordinateSystem)
	 */
	@Override
	public void setCurrentCoordinateSystem(ICoordinateSystem cs) throws GkException {
		communicator.send( GkUtils.toBytesList( cs.getCode()) );
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.ICoordinateSystemAdapter#resetCurrentCoordinateSystem()
	 */
	@Override
	public void resetCurrentCoordinateSystem() throws GkException {
		ICoordinateSystem current = getCurrentCoordinateSystem();
		Tuple6b offsets = getCoordinateSystemOffset(current);
		Tuple6b mPos = new Tuple6b(tinygState.getWorkPosition());
		mPos = mPos.add(offsets);
		String cmd = "{\""+String.valueOf(current) +"\":{";

		cmd += "\"x\":"+ getPositionAsString(mPos.getX()) +", ";
		cmd += "\"y\":"+ getPositionAsString(mPos.getY())+", ";
		cmd += "\"z\":"+ getPositionAsString(mPos.getZ())+"}} ";
		communicator.send( GkUtils.toBytesList( cmd ) );
		communicator.updateCoordinateSystem(current);
	}

	/**
	 * @return
	 */
	@Override
	public boolean isPlannerBufferSpaceCheck() {
		return TinyGPreferences.getInstance().isPlannerBufferSpaceCheck();
	}
	/**
	 * @param plannerBufferSpaceCheck the plannerBufferSpaceCheck to set
	 * @throws GkTechnicalException
	 */
	@Override
	public void setPlannerBufferSpaceCheck(boolean value) throws GkTechnicalException {
		TinyGPreferences.getInstance().setPlannerBufferSpaceCheck(value);
	}
	/**
	 * @return the monitorService
	 */
	public IExecutionService<ExecutionTokenState, IExecutionToken<ExecutionTokenState>> getMonitorService() {
		return executionService;
	}
	/**
	 * @param monitorService the monitorService to set
	 * @throws GkException GkException
	 */
	public void setMonitorService(IExecutionService<ExecutionTokenState, IExecutionToken<ExecutionTokenState>> monitorService) throws GkException {
		this.executionService = monitorService;
		this.executionService.setExecutor(tinygExecutor);
		this.executionService.addExecutionListener(tinygExecutor);
		//executionService.setExecutor(new DebugExecutor());
	}

	public Unit<Length> getCurrentUnit(){
		return tinygState.getCurrentUnit();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IWorkVolumeProvider#getWorkVolumeMaximalPosition()
	 */
	@Override
	public Tuple6b getWorkVolumeMaximalPosition() throws GkException {
		Tuple6b max = findWorkVolumeMaximalPosition();
		if(max == null){
			 throw new GkTechnicalException("No maximal position currently defined for the travel max position");
		}
		return max;
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IWorkVolumeProvider#getWorkVolumeMinimalPosition()
	 */
	@Override
	public Tuple6b getWorkVolumeMinimalPosition() throws GkException {
		Tuple6b min = findWorkVolumeMinimalPosition();
		if(min == null){
			 throw new GkTechnicalException("No minimal position currently defined for the travel max position");
		}
		return min;
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IWorkVolumeProvider#findWorkVolumeMaximalPosition()
	 */
	@Override
	public Tuple6b findWorkVolumeMaximalPosition() throws GkException {
		TinyGConfiguration cfg = getConfiguration();
		Tuple6b max = null;
		if(cfg != null){
			max = new Tuple6b(Units.MILLIMETRE, AngleUnit.DEGREE_ANGLE);
			max.setX( Length.valueOf( cfg.getSetting(TinyGConfiguration.X_AXIS_SETTINGS, TinyGAxisSettings.TRAVEL_MAXIMUM, BigDecimal.class), Units.MILLIMETRE));
			max.setY( Length.valueOf( cfg.getSetting(TinyGConfiguration.Y_AXIS_SETTINGS, TinyGAxisSettings.TRAVEL_MAXIMUM, BigDecimal.class), Units.MILLIMETRE));
			max.setZ( Length.valueOf( cfg.getSetting(TinyGConfiguration.Z_AXIS_SETTINGS, TinyGAxisSettings.TRAVEL_MAXIMUM, BigDecimal.class), Units.MILLIMETRE));
		}
		return max;
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IWorkVolumeProvider#findWorkVolumeMinimalPosition()
	 */
	@Override
	public Tuple6b findWorkVolumeMinimalPosition() throws GkException {
		TinyGConfiguration cfg = getConfiguration();
		Tuple6b min = null;
		if(cfg != null){
			min = new Tuple6b(Units.MILLIMETRE, AngleUnit.DEGREE_ANGLE);
			min.setX( Length.valueOf( cfg.getSetting(TinyGConfiguration.X_AXIS_SETTINGS, TinyGAxisSettings.TRAVEL_MINIMUM, BigDecimal.class), Units.MILLIMETRE));
			min.setY( Length.valueOf( cfg.getSetting(TinyGConfiguration.Y_AXIS_SETTINGS, TinyGAxisSettings.TRAVEL_MINIMUM, BigDecimal.class), Units.MILLIMETRE));
			min.setZ( Length.valueOf( cfg.getSetting(TinyGConfiguration.Z_AXIS_SETTINGS, TinyGAxisSettings.TRAVEL_MINIMUM, BigDecimal.class), Units.MILLIMETRE));
	}
		return min;
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerConfigurationFileExporter#getFileExtension()
	 */
	@Override
	public String getFileExtension() {
		return "tinyg.cfg";
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerConfigurationFileExporter#canExport()
	 */
	@Override
	public boolean canExport() throws GkException {
		return MachineState.READY.equals(getState())
				|| MachineState.PROGRAM_STOP.equals(getState())
				|| MachineState.PROGRAM_END.equals(getState());
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerConfigurationFileExporter#exportTo(java.io.OutputStream)
	 */
	@Override
	public void exportTo(OutputStream stream) throws GkException {
		JsonObject json = TinyGJsonUtils.toJson(getConfiguration());
		try {
			stream.write(json.toString().getBytes());
		} catch (IOException e) {
			throw new GkTechnicalException(e);
		}
	}
	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerConfigurationFileImporter#canImport()
	 */
	@Override
	public boolean canImport() throws GkException {
		return canExport();
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IControllerConfigurationFileImporter#importFrom(java.io.InputStream)
	 */
	@Override
	public void importFrom(InputStream inputStream) throws GkException {
		TinyGConfiguration currentConfiguration = getConfiguration();
		try {
			JsonObject jsonCfg = JsonObject.readFrom(new InputStreamReader(inputStream));
			TinyGControllerUtility.handleConfigurationModification(currentConfiguration, jsonCfg);
			updateConfiguration(currentConfiguration);
		} catch (IOException e) {
			throw new GkTechnicalException(e);
		}
	}

	/**
	 * @return the eventAdmin
	 */
	public EventAdmin getEventAdmin() {
		return eventAdmin;
	}
	/**
	 * @param eventAdmin the eventAdmin to set
	 */
	public void setEventAdmin(EventAdmin eventAdmin) {
		this.eventAdmin = eventAdmin;
	}

	/** (inheritDoc)
	 * @see org.goko.controller.tinyg.controller.ITinygControllerService#killAlarm()
	 */
	@Override
	public void killAlarm() throws GkException {
		communicator.send(GkUtils.toBytesList("{\"clear\":\"\"}"));
	}

	/** (inheritDoc)
	 * @see org.goko.core.controller.IJogService#jog(org.goko.core.controller.bean.EnumControllerAxis, org.goko.core.common.measure.quantity.Length, org.goko.core.common.measure.quantity.Speed)
	 */
	@Override
	public void jog(EnumControllerAxis axis, Length step, Speed feedrate) throws GkException {
		tinyGJogging.jog(axis, step, feedrate);
	}
	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onQueueExecutionStart()
	 */
	@Override
	public void onQueueExecutionStart() throws GkException {}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionStart(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionStart(IExecutionToken<ExecutionTokenState> token) throws GkException {}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionCanceled(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionCanceled(IExecutionToken<ExecutionTokenState> token) throws GkException {}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionPause(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionPause(IExecutionToken<ExecutionTokenState> token) throws GkException {}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionResume(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionResume(IExecutionToken<ExecutionTokenState> token) throws GkException {}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onExecutionComplete(org.goko.core.gcode.execution.IExecutionToken)
	 */
	@Override
	public void onExecutionComplete(IExecutionToken<ExecutionTokenState> token) throws GkException {}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onQueueExecutionComplete()
	 */
	@Override
	public void onQueueExecutionComplete() throws GkException {
		clearProbingGCode();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeTokenExecutionListener#onQueueExecutionCanceled()
	 */
	@Override
	public void onQueueExecutionCanceled() throws GkException {
		clearProbingGCode();
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.service.IGCodeLineExecutionListener#onLineStateChanged(org.goko.core.gcode.execution.IExecutionToken, java.lang.Integer)
	 */
	@Override
	public void onLineStateChanged(IExecutionToken<ExecutionTokenState> token, Integer idLine) throws GkException {}
}
