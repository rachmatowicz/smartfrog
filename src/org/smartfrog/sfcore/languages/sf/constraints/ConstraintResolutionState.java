/** (C) Copyright 1998-2008 Hewlett-Packard Development Company, LP

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

For more information: www.smartfrog.org

 */
package org.smartfrog.sfcore.languages.sf.constraints;

import java.util.HashMap;
import java.util.Vector;

import org.smartfrog.sfcore.common.Context;
import org.smartfrog.sfcore.common.SmartFrogResolutionException;
import org.smartfrog.sfcore.common.SmartFrogRuntimeException;
import org.smartfrog.sfcore.componentdescription.ComponentDescription;
import org.smartfrog.sfcore.languages.sf.sfcomponentdescription.SFComponentDescriptionImpl;
import org.smartfrog.sfcore.languages.sf.sfcomponentdescription.SFComponentDescriptionImpl.LRSRecord;
import org.smartfrog.sfcore.languages.sf.sfreference.SFApplyReference;
import org.smartfrog.sfcore.reference.Reference;

/**
 * Maintains state of constraint resolution, done as part of link resolution
 * @author anfarr
 *
 */
public class ConstraintResolutionState {

    /**
     * Records link resolution history.  Concerned with maintaining undo information when constraints are used
     */
    //It is necessary to maintain link history separately as backtracking occurs to a finer granularity than Constraint types
    private Vector<LinkHistoryRecord> linkHistory = new Vector<LinkHistoryRecord>();
    /**
     * Maintains a history of Constraint goals that have been (successfully) evaluated
     */
    private Vector<ConstraintEvalHistoryRecord> constraintEvalHistory = new Vector<ConstraintEvalHistoryRecord>();
    /**
     * Indicates current link history record being used.
     */
    private LinkHistoryRecord currentLHRecord;
    /**
     * Indicates current constrain eval history record being used.
     */
    private ConstraintEvalHistoryRecord currentCEHRecord;
    /**
     * Used to maintain whether backtracking -- as part of constraint solving -- has just occurred in processing current attribute. 
     */
    private ConstraintContext backtrackedTo = null;
    
    /**
     * Constructs ConstraintResolutionState, allocating a link history record
     */
    public ConstraintResolutionState() {
        currentLHRecord = new LinkHistoryRecord();
        linkHistory.add(currentLHRecord);
    }
    /**
     * Gets whether backtracking has occurred recently
     * @return backtracking flag
     */
    public ConstraintContext hasBacktrackedTo() {
        return backtrackedTo;
    }

    /**
     * Resets flag wrt backtracking
     */
    public void resetDoneBacktracking() {
        backtrackedTo = null;
    }
    /**
     * Indicates whether manipulations of context attributes should currently have undo records created for them
     */
    private boolean constraintsShouldUndo = false;

    /**
     * Sets whether manipulations of context attributes should currently have undo records created for them
     * @param shouldUndo
     */
    public void setConstraintsShouldUndo(boolean shouldUndo) {
        constraintsShouldUndo = shouldUndo;
    }

    /**
     * Maintains labelling records for automatic variables
     */
    private HashMap<Object, Vector<FreeVar>> labellingRecord = new HashMap<Object, Vector<FreeVar>>(); 
     
    
    /**
     * Add a labelling record against key for entry; when key is reached in link resolution, recorded autos are assigned
     * @param key against which FreeVar entry is recorded
     * @param entry automatic variable requiring labelling
     */
    public void addLabellingRecord(Object key, FreeVar entry){
    	Vector<FreeVar> vec = labellingRecord.get(key);
    	if (vec==null) {
    		vec = new Vector<FreeVar>();
    		labellingRecord.put(key, vec);
    		addUndoLRVector(key);
    	}
    	vec.add(entry);
    	addUndoLREntry(vec, entry);
    }
    
    /**
     * Removes labelling record vector for key
     * @param key vector to be removed
     * @return removed vector
     */
    public Vector<FreeVar> removeLabellingRecordVector(Object key){
    	Vector<FreeVar> vec=labellingRecord.remove(key);
    	if (vec!=null) addUndoLRVector(key, vec);
    	return vec;
    }
    
    /**
     * Maintains a record pertaining to a single constraint evaluation
     * @author anfarr
     *
     */
    private class ConstraintEvalHistoryRecord {

        /**
         * Indicates the current LRSRecord being processed 
         */
        LRSRecord lrsr;
        /**
         * Indicates the current attribute being processed, triggering constraint evaluation 
         */
        int idx;
        /**
         * The context of the component description being processed
         */
        ConstraintContext cc;
     
        /**
         * Assigned freevars with autoeffects
         */
        Vector<FreeVar> fvs = new Vector<FreeVar>();
        
    }

    /**
     * Link history record
     */
    private class LinkHistoryRecord {

        /**
         * The undo stack for this record
         */
        Vector<LRSUndoRecord> undo_stack = new Vector<LRSUndoRecord>();

        /**
         * Add a single undo record
         * @param lrsu undo record to add
         */
        void addUndo(LRSUndoRecord lrsu) {
            undo_stack.add(lrsu);
        }

        /**
         * Undo all actions recorded herein
         */
        void undoAll() {
            for ( int i = undo_stack.size() - 1; i >= 0; i-- ) {
                undo_stack.remove(i).undo();
            }
        }
    }

    /**
     * Abstract class for undo actions
     */
    private abstract class LRSUndoRecord {

        /**
         * Does the undo!
         *
         */
        abstract void undo();
    }

    /**
     * Put in context undo action
     */
    private class LRSUndoPut extends LRSUndoRecord {

        /**
         * Pertinent context for undo action
         */
        Context ctxt;
        /**
         * Key for undo action
         */
        Object key;
        /**
         * Value to eg put back in context for key
         */
        Object value;

        /**
         * Constructs single undo action to undo attribute setting in a Context
         * @param ctxt context for undo action
         * @param key  key to undo put value
         * @param value value to restore
         */
        LRSUndoPut(Context ctxt, Object key, Object value) {
            this.ctxt = ctxt;
            this.key = key;
            this.value = value;
        }

        /**
         * Does the undo!
         *
         */
        void undo() {
            if ( value != null ) {
                ctxt.put(key, value);
            } else {
                ctxt.remove(key);
            }
        }
    }

    /**
     * Undo on FreeVar info setting
     */
    private class LRSUndoFVInfo extends LRSUndoRecord {

        /**
         * FreeVar to undo information setting on...
         */
        FreeVar fv;

        /**
         * Constructs single undo action for undoing FreeVar manipulation
         * @param fv  FreeVar
         * @param type What type of undoing should we do?  Either undo type setting (g_LRSUndo_PUTFVTYPESTR) or cons eval info (g_LRSUndo_PUTFVINFO)
         */
        LRSUndoFVInfo(FreeVar fv) {
            this.fv = fv;
        }

        /**
         * Does the undo!
         *
         */
        void undo() {
            fv.resetConsEvalInfo();
        }
    }
    
    /**
     * Undo on FreeVar info setting
     */
    private class LRSUndoFVAutoVarEffect extends LRSUndoRecord {

        /**
         * FreeVar to undo information setting on...
         */
        FreeVar fv;

        Vector<Reference> autoEffects;
        
        /**
         * Constructs single undo action for undoing FreeVar manipulation
         * @param fv  FreeVar
         * @param type What type of undoing should we do?  Either undo type setting (g_LRSUndo_PUTFVTYPESTR) or cons eval info (g_LRSUndo_PUTFVINFO)
         */
        LRSUndoFVAutoVarEffect(FreeVar fv) {
            this.fv = fv;
        }
        
        /**
         * Constructs single undo action for undoing FreeVar manipulation
         * @param fv  FreeVar
         * @param type What type of undoing should we do?  Either undo type setting (g_LRSUndo_PUTFVTYPESTR) or cons eval info (g_LRSUndo_PUTFVINFO)
         */
        LRSUndoFVAutoVarEffect(FreeVar fv, Vector<Reference> autoEffects) {
            this.fv = fv;
        }

        /**
         * Does the undo!
         *
         */
        void undo() {
        	if (autoEffects!=null){
        		fv.setAutoEffects(autoEffects);
        	} else {
        		fv.removeAutoEffects();
        	}
        }
    }
    
    /**
     * Undo on FreeVar info setting
     */
    private class LRSUndoCEHRFVPut extends LRSUndoRecord {

        /**
         * FreeVar to undo information setting on...
         */
        FreeVar fv;

        ConstraintEvalHistoryRecord cehr;
        
        /**
         * Constructs single undo action for undoing FreeVar manipulation
         * @param fv  FreeVar
         * @param type What type of undoing should we do?  Either undo type setting (g_LRSUndo_PUTFVTYPESTR) or cons eval info (g_LRSUndo_PUTFVINFO)
         */
        LRSUndoCEHRFVPut(ConstraintEvalHistoryRecord cehr, FreeVar fv) {
            this.fv = fv;
            this.cehr = cehr;
        }

        /**
         * Does the undo!
         *
         */
        void undo() {
        	cehr.fvs.remove(fv);
        }
    }

    

    /**
     * Undo on FreeVar type string setting
     */
    private class LRSUndoFVTypeStr extends LRSUndoRecord {

        /**
         * FreeVar to undo type string setting on...
         */
        FreeVar fv;

        /**
         * Constructs single undo action for undoing FreeVar manipulation
         * @param fv  FreeVar
         * @param type What type of undoing should we do?  Either undo type setting (g_LRSUndo_PUTFVTYPESTR) or cons eval info (g_LRSUndo_PUTFVINFO)
         */
        LRSUndoFVTypeStr(FreeVar fv) {
            this.fv = fv;
        }

        /**
         * Does the undo!
         *
         */
        void undo() {
            fv.clearTyping();
        }
    }


    /**
     *
     */
    private class LRSUndoLRVector extends LRSUndoRecord {

        /**
         * Key for undo action
         */
        Object key;
        /**
         * 
         */
        Vector<FreeVar> vec;
                
        /**
         * 
         * @param key  
         */
        LRSUndoLRVector(Object key, Vector<FreeVar> vec) {
            this.key = key;
            this.vec = vec;
        }
        
        /**
         * 
         * @param key  
         */
        LRSUndoLRVector(Object key) {
            this.key = key;
        }
        
        
        /**
         * Does the undo!
         *
         */
        void undo() {
            if ( vec != null ) {
                labellingRecord.put(key, vec);
            } else {
                labellingRecord.remove(key);
            }
        }
    }
    
    /**
    *
    */
   private class LRSUndoLREntry extends LRSUndoRecord {

       /**
        * 
        */
	   Vector<FreeVar> vec;
	   
       /**
        * 
        */
       FreeVar entry;
               
       /**
        * 
        * @param key  
        */
       LRSUndoLREntry(Vector<FreeVar> vec, FreeVar entry) {
           this.vec = vec;
           this.entry = entry;
       }
       
       /**
        * Does the undo!
        *
        */
       void undo() {
           vec.remove(entry);
       }
   }
    
    /**
     * Adds single undo action to current lhr for undo attribute setting in a Context
     * @param ctxt context for undo action
     * @param key  key to undo put value
     * @param value value to restore
     */
    public void addUndoPut(Context ctxt, Object key, Object value) {
        if ( constraintsShouldUndo ) {
            currentLHRecord.addUndo(new LRSUndoPut(ctxt, key, value));
        }
    }
    
    public void addUndoLRVector(Object key, Vector<FreeVar> vec) {
        currentLHRecord.addUndo(new LRSUndoLRVector(key,vec));
    }
    
    public void addUndoLRVector(Object key) {
        currentLHRecord.addUndo(new LRSUndoLRVector(key));
    }
    
    public void addUndoFVAutoVarEffect(FreeVar fv, Vector<Reference> autoEffects){
    	currentLHRecord.addUndo(new LRSUndoFVAutoVarEffect(fv, autoEffects));
    }
    
    public void addUndoFVAutoVarEffect(FreeVar fv){
    	currentLHRecord.addUndo(new LRSUndoFVAutoVarEffect(fv));
    }
    
    
    public void addUndoLREntry(Vector<FreeVar> vec, FreeVar fv) {
        currentLHRecord.addUndo(new LRSUndoLREntry(vec, fv));
    }

    /**
     * Adds single undo action to current lhr for undoing FreeVar info stetting
     * @param fv  FreeVar
     */
    public void addUndoFVInfo(FreeVar fv) {
        currentLHRecord.addUndo(new LRSUndoFVInfo(fv));
    }

    /**
     * Adds single undo action to current lhr for undoing FreeVar type string stetting
     * @param fv  FreeVar
     */
    public void addUndoFVTypeStr(FreeVar fv) {
        currentLHRecord.addUndo(new LRSUndoFVTypeStr(fv));
    }

    /**
     * Sets typing for FreeVar attribute in current constraint context
     * @param attr FreeVar attribute
     * @param types Vector of type references representing typing
     */
    public void setTyping(String attr, Vector types) {
        //Get value object for attribute
        Object val = currentCEHRecord.cc.comp.sfContext().get(attr);
        if ( val instanceof FreeVar ) {
            //And if FreeVar...
            FreeVar fv = (FreeVar) val;
            //Set typing information...
            fv.setTyping(types);
            //need to add an undo record for the typing...
            addUndoFVTypeStr(fv);
        }
    }

    /**
     * If a value to-be-set for a FreeVar is the name of an attribute in the 
     * current context and which resolves to a component description, 
     * then protocol dictates that we want to use this attribute's value...
     * @param key Value to be provisionally used in set
     * @return Given key unless key is an attrribute in current context which
     * resolves to a component description, then returns value of said attribute
     */
    public Object adjustSetValue(Object key) {
        //Is the key an attribute?
        Object val = currentCEHRecord.cc.comp.sfContext().get(key);

        //If so, and component description then return val else return key...
        if ( val != null && val instanceof ComponentDescription ) {
            return val;
        } else {
            return key;
        }
    }

    /**
     * Add an assignment of an attribute within a description
     * @param solve_comp ComponentDescription pertaining to current Constraint context
     * @param key Attribute to set 
     * @param val Value to set
     * @param cidx The appropriate constraint eval record
     */
    public boolean addConstraintAss(ComponentDescription solve_comp, String key, Object val, int cidx)
            throws SmartFrogResolutionException {
        //Get constraint record pertaining to the attribute to be set...
        ConstraintEvalHistoryRecord cehr = constraintEvalHistory.get(cidx);

        //Get typing information for attribute to be set...
        Object cur_val = cehr.cc.comp.sfContext().get(key);
        Vector types = null;
        if ( cur_val instanceof FreeVar ) {
            types = ((FreeVar) cur_val).getTyping();        //If value to be set is not of correct type then bail...
        }
        if ( types != null && (!(val instanceof ComponentDescription) ||
                !ofTypes(solve_comp, (ComponentDescription) val, types)) ) {
            return false;        //set the value prescribed
        }
        constraintsShouldUndo = true;
        if ( cur_val instanceof FreeVar && ((FreeVar) cur_val).getRange() instanceof Boolean){
        	if (val instanceof Integer) {
        		int ival = ((Integer) val).intValue();
        		if (ival==0 || ival==1){
        			Boolean bval = new Boolean((ival==1?true:false));
        			try{cehr.cc.comp.sfReplaceAttribute(key, bval);}catch (SmartFrogRuntimeException e){/**/}
        			
        		} else throw new SmartFrogResolutionException("Trying set Boolean value for key: "+key+" in: "+cehr.cc.comp+" when value to set is not 1 or 0");
        	} else throw new SmartFrogResolutionException("Trying set Boolean value for key: "+key+" in: "+cehr.cc.comp+" when value to set is not 1 or 0");
        } else { 
            try{cehr.cc.comp.sfReplaceAttribute(key, val);}catch (SmartFrogRuntimeException e){/**/}
        }
        
        if ( cur_val instanceof FreeVar ) {
        	
        	FreeVar cur_fv = (FreeVar) cur_val;
        	if ( cur_fv.isAutoEffects() && !currentCEHRecord.fvs.contains(cur_val) ) {
        		currentCEHRecord.fvs.add(cur_fv);
        		cur_fv.setAssignedValue(val);
        	}
        }
        
        //System.out.println("Setting "+key+":"+cehr.cc.comp.sfContext().get(key)+" in "+cidx);
        constraintsShouldUndo = false;

        return true;
    }

    /** Checks that a candidate component description is of a given typing 
     * @param solve_comp ComponentDescription pertaining to current Constraint context
     * @param comp ComponentDescription representing candidate value for setting
     * @param types Vector of type references representing typing
     * @return true if of given typing, false otherwise
     * @throws SmartFrogResolutionException if the composition fails
     */
    public boolean ofTypes(ComponentDescription solve_comp, ComponentDescription comp, Vector types)
            throws SmartFrogResolutionException {
        Context type_cxt = null;
        //Compose the benchmark type
        try {
            type_cxt = SFComponentDescriptionImpl.composeTypes(solve_comp, types).sfContext();
        } catch (SmartFrogResolutionException smfre) {
            throw new SmartFrogResolutionException("Unable to compose types in sub-type evaluation.");
        }
        //Return if of composed benchmark type...
        return type_cxt.ofType(comp);
    }

    public void backtrackConstraintAss(int idx, int cidx) {
	    //Get constraint record pertaining to current position in constraint solving
        ConstraintEvalHistoryRecord cehr = constraintEvalHistory.get(cidx);

        //Have we backtracked?
        if (backtrackedTo==null) backtrackedTo = (cehr==currentCEHRecord? null:cehr.cc);
        
        //System.out.println("CIDX STATUS:"+cidx+":"+constraintEvalHistoryLastIdx);
        for ( int i = constraintEvalHistory.size()-1; i > cidx; i-- ) {
            constraintEvalHistory.remove(i);
        }
        
        currentCEHRecord = cehr;
        
        if (CoreSolver.getInstance().getRootDescription()!=null){
        	CoreSolver.getInstance().getRootDescription().setLRSIdx(cehr.idx);
        	CoreSolver.getInstance().getRootDescription().setLRSRecord(cehr.lrsr);
        }
        
        //Backtrack histroy as approp...
        for ( int i = linkHistory.size() - 1; i > idx; i-- ) {
            linkHistory.remove(i).undoAll();        //Create new history...
        }
        currentLHRecord = new LinkHistoryRecord();
        linkHistory.add(currentLHRecord);
    }

    /**
     * Add a record to cons eval history
     * @param cc Given constraint context
     * @return Latest record index
     */
    public int addConstraintEval(ConstraintContext cc) {
    	int idx = constraintEvalHistory.size();
        ConstraintEvalHistoryRecord cehr = new ConstraintEvalHistoryRecord();
        if (CoreSolver.getInstance().getRootDescription()!=null){
        	cehr.lrsr = CoreSolver.getInstance().getRootDescription().getLRSRecord();
        	cehr.idx = CoreSolver.getInstance().getRootDescription().getLRSIdx();
        }
        cehr.cc = cc;
        cc.cehr = cehr;
        constraintEvalHistory.add(cehr);
        currentCEHRecord = cehr;
        return idx;
    }

    /**
     * Gets the latest record index of the cons eval history
     * @return latest index
     */
    public int getConsEvalIdx() {
        return constraintEvalHistory.size() - 1;
    }
    
    public static class ConstraintContext {
    	public ConstraintContext(ComponentDescription p, ComponentDescription comp, Object key, Reference ar, Object ret_key){
    		this.p=p; this.comp=comp; this.key=key; this.ar=ar; this.ret_key=ret_key;
    	}
    	Reference ar;   
    	ComponentDescription p;
    	ComponentDescription comp;
    	Object key;
    	Object ret_key;
    	ConstraintEvalHistoryRecord cehr;
    	
    	
    	public Reference getAR(){return ar;}    
    	public ComponentDescription getParent(){return p;}
    	public ComponentDescription getCD(){return comp;}
    	public Object getKey(){return key;}
    	public Vector<FreeVar> getFVs(){return cehr.fvs;}
    	public Object getRetKey(){return ret_key; }
    }
}
