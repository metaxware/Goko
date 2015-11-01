/**
 * 
 */
package org.goko.gcode.rs274ngcv3.ui.workspace.uiprovider;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.swt.graphics.Image;
import org.goko.core.common.exception.GkException;
import org.goko.core.gcode.element.IGCodeProvider;
import org.goko.core.gcode.rs274ngcv3.IRS274NGCService;
import org.goko.core.gcode.rs274ngcv3.context.GCodeContext;
import org.goko.core.gcode.rs274ngcv3.element.GCodeProvider;
import org.goko.core.gcode.rs274ngcv3.element.IModifier;
import org.goko.core.log.GkLog;
import org.goko.core.workspace.bean.ProjectContainer;
import org.goko.core.workspace.bean.ProjectContainerUiProvider;

/**
 * @author PsyKo
 * @date 31 oct. 2015
 */
public class GCodeContainerUiProvider extends ProjectContainerUiProvider {
	/** LOG */
	private static final GkLog LOG = GkLog.getLogger(GCodeContainerUiProvider.class);	
	/** GCode service */
	private IRS274NGCService rs274Service;
	private ITreeContentProvider contentProvider;
	private IStyledLabelProvider labelProvider;

	/**
	 * @param rs274Service 
	 * @param type
	 */
	public GCodeContainerUiProvider(IRS274NGCService rs274Service) {
		super("TEST");
		this.rs274Service = rs274Service;
		this.labelProvider = new GCodeContainerLabelProvider();
	}

	/** (inheritDoc)
	 * @see org.goko.core.workspace.bean.ProjectContainerUiProvider#providesLabelFor(java.lang.Object)
	 */
	@Override
	public boolean providesLabelFor(Object content) throws GkException {
		if(content instanceof ProjectContainer){
			return StringUtils.equals(getType(), ((ProjectContainer) content).getType());
		}
		return (content instanceof IGCodeProvider)
			|| (content instanceof IModifier);
	}
	
	/** (inheritDoc)
	 * @see org.goko.core.workspace.bean.ProjectContainerUiProvider#getStyledText(java.lang.Object)
	 */
	@Override
	public StyledString getStyledText(Object element) {		
		return labelProvider.getStyledText(element);
	}
	
	/** (inheritDoc)
	 * @see org.goko.core.workspace.bean.ProjectContainerUiProvider#getImage(java.lang.Object)
	 */
	@Override
	public Image getImage(Object element) {		
		return labelProvider.getImage(element);
	}

	/** (inheritDoc)
	 * @see org.goko.core.workspace.bean.ProjectContainerUiProvider#providesContentFor(java.lang.Object)
	 */
	@Override
	public boolean providesContentFor(Object content) throws GkException {
		if(content instanceof ProjectContainer){
			return StringUtils.equals(getType(), ((ProjectContainer) content).getType());
		}
		return content instanceof GCodeProvider;
	}

	/** (inheritDoc)
	 * @see org.goko.core.workspace.bean.ProjectContainerUiProvider#hasChildren(java.lang.Object)
	 */
	@Override
	public boolean hasChildren(Object content) throws GkException {
		if(content instanceof GCodeProvider){
			return true;
		}else if(content instanceof ProjectContainer){			
			return CollectionUtils.isNotEmpty(rs274Service.getGCodeProvider());			
		}
		return false;
	}

	/** (inheritDoc)
	 * @see org.goko.core.workspace.bean.ProjectContainerUiProvider#getChildren(java.lang.Object)
	 */
	@Override
	public Object[] getChildren(Object content) throws GkException {
		if(content instanceof GCodeProvider){
			return new Object[]{new IModifier() {
				
				@Override
				public void setId(Integer id) {
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public Integer getId() {
					// TODO Auto-generated method stub
					return null;
				}
				
				@Override
				public void apply(GCodeContext initialContext, GCodeProvider source, GCodeProvider target) throws GkException {
					// TODO Auto-generated method stub
					
				}
			}};
		}else if(content instanceof ProjectContainer){			
			return rs274Service.getGCodeProvider().toArray();			
		}
		return null;
	}
	
	/** (inheritDoc)
	 * @see org.goko.core.workspace.bean.ProjectContainerUiProvider#getParent(java.lang.Object)
	 */
	@Override
	public Object getParent(Object content) throws GkException {		
		return null;
	}
}
