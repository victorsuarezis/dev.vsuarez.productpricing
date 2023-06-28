/**
 * 
 */
package dev.vsuarez.model;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Properties;

import org.compiere.model.MDiscountSchemaBreak;
import org.compiere.model.MProductCategory;
import org.compiere.util.DB;

/**
 * @author <a href="mailto:victor.suarez.is@gmail.com">Ing. Victor Suarez</a>
 *
 */
public class IVS_MDiscountSchemaBreak extends MDiscountSchemaBreak {

	/**
	 * 
	 */
	private static final long serialVersionUID = -686576004998408314L;
	
	private static final String COLUMNNAME_M_Warehouse_ID = "M_Warehouse_ID";
	private static final String COLUMNNAME_M_PriceList_ID = "M_PriceList_ID";
	private static final String COLUMNNAME_IVS_Brand_ID = "IVS_Brand_ID";

	/**
	 * @param ctx
	 * @param M_DiscountSchemaBreak_ID
	 * @param trxName
	 */
	public IVS_MDiscountSchemaBreak(Properties ctx, int M_DiscountSchemaBreak_ID, String trxName) {
		super(ctx, M_DiscountSchemaBreak_ID, trxName);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param ctx
	 * @param rs
	 * @param trxName
	 */
	public IVS_MDiscountSchemaBreak(Properties ctx, ResultSet rs, String trxName) {
		super(ctx, rs, trxName);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param copy
	 */
	public IVS_MDiscountSchemaBreak(MDiscountSchemaBreak copy) {
		super(copy);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param ctx
	 * @param copy
	 */
	public IVS_MDiscountSchemaBreak(Properties ctx, MDiscountSchemaBreak copy) {
		super(ctx, copy);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param ctx
	 * @param copy
	 * @param trxName
	 */
	public IVS_MDiscountSchemaBreak(Properties ctx, MDiscountSchemaBreak copy, String trxName) {
		super(ctx, copy, trxName);
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * 	Criteria apply
	 *	@param Value amt or qty
	 *	@param M_Product_ID product
	 *	@param M_Product_Category_ID category
	 *	@param M_Warehouse_ID warehouse
	 *	@param M_PriceList Price List
	 *	@return true if criteria met
	 */
	public boolean applies (BigDecimal Value, int M_Product_ID, int M_Product_Category_ID, int M_Warehouse_ID, int M_PriceList_ID) {
		if (!isActive())
			return false;
		
		//	below break value
		if (Value.compareTo(getBreakValue()) < 0)
			return false;
		
		//	No Product / Category / Warehouse / PriceList / Brand
		if (getM_Product_ID() == 0 && getM_Product_Category_ID() == 0
				&& getM_Warehouse_ID() == 0 && getM_PriceList_ID() == 0 && getIVS_Brand_ID() == 0)
			return true;
		
		//	Product
		if (getM_Product_ID() == M_Product_ID)
			return true;
		
		// Warehouse and Price List
		if(getM_Warehouse_ID() > 0 && getM_PriceList_ID() > 0)
			return getM_Warehouse_ID() == M_Warehouse_ID && getM_PriceList_ID() == M_PriceList_ID;
		
		// Brand and Price List
		if(getIVS_Brand_ID() > 0 && getM_PriceList_ID() > 0)
			return getIVS_Brand_ID() == getIVS_Brand_ID(M_Product_ID) && getM_PriceList_ID() == M_PriceList_ID; 
		
		if(getM_Warehouse_ID() > 0)
			if(getM_Warehouse_ID() == M_Warehouse_ID)
				return true;
		if(getM_PriceList_ID() > 0)
			if(getM_PriceList_ID() == M_PriceList_ID)
				return true;
		if(getIVS_Brand_ID() > 0)
			return getIVS_Brand_ID() == getIVS_Brand_ID(M_Product_ID);
		
		//	Category
		if (M_Product_Category_ID != 0)
			return getM_Product_Category_ID() == M_Product_Category_ID;

		//	Look up Category of Product
		return MProductCategory.isCategory(getM_Product_Category_ID(), M_Product_ID);
	}	//	applies

	/**
	 * Get Warehouse 
	 * @return M_Warehouse_ID
	 */
	public int getM_Warehouse_ID() {
		return get_ValueAsInt(COLUMNNAME_M_Warehouse_ID );
	}
	
	/**
	 * Get Price List
	 * @return M_PriceList_ID
	 */
	public int getM_PriceList_ID() {
		return get_ValueAsInt(COLUMNNAME_M_PriceList_ID);
	}
	
	/**
	 * Set Warehouse
	 */
	public void setM_Warehouse_ID(int M_Warehouse_ID) {
		if(M_Warehouse_ID > 0)
			set_Value(COLUMNNAME_M_Warehouse_ID, M_Warehouse_ID);
	}
	
	/**
	 * Set Price List
	 */
	public void setM_PriceList_ID(int M_PriceList_ID) {
		if(M_PriceList_ID > 0)
			set_Value(COLUMNNAME_M_PriceList_ID, M_PriceList_ID);
	}
	
	/**
	 * Set Brand
	 */
	public void setIVS_Brand_ID(int IVS_Brand_ID) {
		if(IVS_Brand_ID > 0)
			set_Value(COLUMNNAME_IVS_Brand_ID, IVS_Brand_ID);
	}
	
	/**
	 * Get Brand
	 * @return IVS_Brand_ID
	 */
	public int getIVS_Brand_ID() {
		return get_ValueAsInt(COLUMNNAME_IVS_Brand_ID);
	}
	
	/**
	 * Brand from Product
	 * @param M_Product_ID
	 * @return IVS_Brand_ID
	 */
	public int getIVS_Brand_ID(int M_Product_ID) {
		String sql = "SELECT ai.M_AttributeValue_ID FROM M_AttributeInstance ai "
				+ " JOIN M_Product p ON p.M_AttributeSetInstance_ID = ai.M_AttributeSetInstance_ID "
				+ " WHERE p.M_Product_ID = ? AND ai.M_AttributeValue_ID =?";
		return DB.getSQLValue(get_TrxName(), sql, M_Product_ID, getIVS_Brand_ID());
	}
	
}
