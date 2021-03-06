package org.goko.core.gcode.rs274ngcv3.instruction.executiontime;

import org.goko.core.common.exception.GkException;
import org.goko.core.common.measure.quantity.Length;
import org.goko.core.common.measure.quantity.Speed;
import org.goko.core.common.measure.quantity.Time;
import org.goko.core.gcode.rs274ngcv3.context.GCodeContext;
import org.goko.core.gcode.rs274ngcv3.element.InstructionType;
import org.goko.core.gcode.rs274ngcv3.instruction.StraightProbeInstruction;
import org.goko.core.math.Tuple6b;

public class StraightProbeTimeCalculator extends AbstractInstructionTimeCalculator<StraightProbeInstruction> {

	public StraightProbeTimeCalculator() {
		super(InstructionType.STRAIGHT_PROBE);
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.rs274ngcv3.instruction.executiontime.AbstractInstructionTimeCalculator#calculateExecutionTime(org.goko.core.gcode.rs274ngcv3.context.GCodeContext, org.goko.core.gcode.rs274ngcv3.instruction.AbstractInstruction)
	 */
	@Override
	protected Time calculateExecutionTime(GCodeContext context, StraightProbeInstruction instruction) throws GkException {
		Tuple6b 		positionBefore 	= context.getPosition();
		Tuple6b 		positionAfter 	= new Tuple6b(instruction.getX(),instruction.getY(),instruction.getZ(),instruction.getA(),instruction.getB(),instruction.getC());

		Tuple6b delta = positionBefore.subtract(positionAfter);
		
		Length max = delta.length();

		Speed feedrate = Speed.ZERO;
		if(context.getFeedrate() != null){
			feedrate = context.getFeedrate();
		}else{
			return Time.ZERO;
		}
		if(feedrate.equals(Speed.ZERO)){
			return Time.ZERO;
		}		
		return max.divide(feedrate);		
	}

}
