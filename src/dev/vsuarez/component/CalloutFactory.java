/**
 * 
 */
package dev.vsuarez.component;

import org.adempiere.base.IColumnCallout;
import org.adempiere.base.IColumnCalloutFactory;
import org.compiere.model.MProductPrice;

import dev.vsuarez.callout.SetProductPrice;

/**
 *  @author <a href="mailto:victor.suarez.is@gmail.com">Ing. Victor Suarez</a>
 *
 */
public class CalloutFactory implements IColumnCalloutFactory {

	@Override
	public IColumnCallout[] getColumnCallouts(String tableName, String columnName) {
		
		if(MProductPrice.Table_Name.equals(tableName)) {
			if(MProductPrice.COLUMNNAME_M_Product_ID.equals(columnName)) {
				return new IColumnCallout[] { new SetProductPrice() };
			}
		}
		return null;
	}

}