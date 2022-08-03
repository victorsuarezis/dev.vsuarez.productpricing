/**
 * 
 */
package dev.vsuarez.component;

import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.util.Properties;

import org.adempiere.base.IModelFactory;
import org.compiere.model.MDiscountSchema;
import org.compiere.model.MDiscountSchemaBreak;
import org.compiere.model.PO;
import org.compiere.util.Env;

import dev.vsuarez.model.IVS_MDiscountSchema;
import dev.vsuarez.model.IVS_MDiscountSchemaBreak;

/**
 * @author <a href="mailto:victor.suarez.is@gmail.com">Ing. Victor Suarez</a>
 *
 */
public class ModelFactory implements IModelFactory {

	@Override
	public Class<?> getClass(String tableName) {
		if(MDiscountSchema.Table_Name.equals(tableName))
			return IVS_MDiscountSchema.class;
		if(MDiscountSchemaBreak.Table_Name.equals(tableName))
			return IVS_MDiscountSchemaBreak.class;
		
		return null;
	}

	@Override
	public PO getPO(String tableName, int Record_ID, String trxName) {
		Class<?> clazz = getClass(tableName);
		
		if(clazz!=null) {
			try {
				return (PO) clazz.getConstructor(Properties.class, int.class, String.class).newInstance(Env.getCtx(),Record_ID, trxName);
			} catch (InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
			}
		}	
		return null;
	}

	@Override
	public PO getPO(String tableName, ResultSet rs, String trxName) {
		Class<?> clazz = getClass(tableName);
		
		if(clazz!=null) {
			try {
				return (PO) clazz.getConstructor(Properties.class, ResultSet.class, String.class).newInstance(Env.getCtx(),rs, trxName);
			} catch (InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

}
