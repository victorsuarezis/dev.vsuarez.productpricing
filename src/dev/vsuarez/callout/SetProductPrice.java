/**
 * 
 */
package dev.vsuarez.callout;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MPriceListVersion;
import org.compiere.model.MProductPrice;
import org.compiere.model.Query;
import org.compiere.util.DB;

/**
 *  @author <a href="mailto:victor.suarez.is@gmail.com">Ing. Victor Suarez</a>
 *
 */
public class SetProductPrice implements IColumnCallout {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) {
		if(value == null)
			return null;
		
		int M_Product_ID = (int) value;
		int M_PriceList_Version_ID = (int) mTab.getValue(MPriceListVersion.COLUMNNAME_M_PriceList_Version_ID);
		MPriceListVersion priceListVersion = new MPriceListVersion(ctx, M_PriceList_Version_ID, null);
		String sql = "SELECT M_Pricelist_Version_ID FROM M_Pricelist_Version "
				+ " WHERE AD_Client_ID =? AND M_PriceList_ID =? AND M_Pricelist_Version_ID != ? AND IsActive = 'Y' "
				+ " ORDER BY ValidFrom DESC ";
		int M_Pricelist_Version_Base_ID = DB.getSQLValue(null, sql, priceListVersion.getAD_Client_ID(), priceListVersion.getM_PriceList_ID(), M_PriceList_Version_ID);
		if(M_Pricelist_Version_Base_ID <= 0)
			return null;
		
		MProductPrice productPrice = new Query(ctx, MProductPrice.Table_Name, "M_PriceList_Version_ID = ? AND M_Product_ID =?", null)
				.setOnlyActiveRecords(true).setParameters(M_Pricelist_Version_Base_ID, M_Product_ID).first();
		if(productPrice == null)
			return null;
		
		mTab.setValue(MProductPrice.COLUMNNAME_PriceList, productPrice.getPriceList());
		mTab.setValue(MProductPrice.COLUMNNAME_PriceStd, productPrice.getPriceStd());
		mTab.setValue(MProductPrice.COLUMNNAME_PriceLimit, productPrice.getPriceLimit());
		
		return null;
	}

}
