package org.goko.core.gcode.rs274ngcv3.instruction.executiontime;

import org.goko.core.common.exception.GkException;
import org.goko.core.common.measure.quantity.Length;
import org.goko.core.common.measure.quantity.Speed;
import org.goko.core.common.measure.quantity.SpeedUnit;
import org.goko.core.common.measure.quantity.Time;
import org.goko.core.gcode.rs274ngcv3.context.GCodeContext;
import org.goko.core.gcode.rs274ngcv3.element.InstructionType;
import org.goko.core.gcode.rs274ngcv3.instruction.StraightTraverseInstruction;
import org.goko.core.math.Tuple6b;

public class StraightTraverseTimeCalculator extends AbstractInstructionTimeCalculator<StraightTraverseInstruction> {

	public StraightTraverseTimeCalculator() {
		super(InstructionType.STRAIGHT_TRAVERSE);
	}

	/** (inheritDoc)
	 * @see org.goko.core.gcode.rs274ngcv3.instruction.executiontime.AbstractInstructionTimeCalculator#calculateExecutionTime(org.goko.core.gcode.rs274ngcv3.context.GCodeContext, org.goko.core.gcode.rs274ngcv3.instruction.AbstractInstruction)
	 */
	@Override
	protected Time calculateExecutionTime(GCodeContext context, StraightTraverseInstruction instruction) throws GkException {
		Tuple6b 		positionBefore 	= context.getPosition();
		Tuple6b 		positionAfter 	= new Tuple6b(instruction.getX(),instruction.getY(),instruction.getZ(),instruction.getA(),instruction.getB(),instruction.getC());

		Tuple6b delta = positionBefore.subtract(positionAfter);
		Length max = delta.length();

		Speed feedrate = Speed.valueOf(1500, SpeedUnit.MILLIMETRE_PER_MINUTE);			
		return max.divide(feedrate);		
	}

}
