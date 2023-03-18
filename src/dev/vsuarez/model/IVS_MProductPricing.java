/**
 * 
 */
package dev.vsuarez.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.logging.Level;

import org.adempiere.base.AbstractProductPricing;
import org.compiere.model.I_C_InvoiceLine;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.model.I_C_ProjectLine;
import org.compiere.model.I_M_RMALine;
import org.compiere.model.I_M_RequisitionLine;
import org.compiere.model.MPriceList;
import org.compiere.model.MProduct;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Trace;

/**
 * @author <a href="mailto:victor.suarez.is@gmail.com">Ing. Victor Suarez</a>
 *
 */
public class IVS_MProductPricing extends AbstractProductPricing {
	
	/** Precision -1 = no rounding		*/
	private int		 	m_precision = -1;
	
	private boolean 	m_useVendorBreak;
	private Boolean		m_found = null;
	private boolean 	m_calculated = false;
	private boolean 	m_vendorbreak = false;
	
	private BigDecimal 	m_PriceList = Env.ZERO;
	private BigDecimal 	m_PriceStd = Env.ZERO;
	private BigDecimal 	m_PriceLimit = Env.ZERO;
	private int 		m_C_Currency_ID = 0;
	private boolean		m_enforcePriceLimit = false;
	private int 		m_C_UOM_ID = 0;
	private int 		m_M_Product_Category_ID;
	private boolean		m_discountSchema = false;
	private boolean		m_isTaxIncluded = false;
	
	private int			m_M_Warehouse_ID = 0;
	
	/**	Logger			*/
	protected CLogger	log = CLogger.getCLogger(getClass());

	/**
	 * 
	 */
	public IVS_MProductPricing() {
	}

	/**
	 * @param M_Product_ID
	 * @param C_BPartner_ID
	 * @param Qty
	 * @param isSOTrx
	 * @param trxName
	 */
	public IVS_MProductPricing(int M_Product_ID, int C_BPartner_ID, BigDecimal Qty, boolean isSOTrx, String trxName) {
		setInitialValues(M_Product_ID, C_BPartner_ID, Qty, isSOTrx, trxName);
	}
	
	@Override
	public void setInitialValues(int M_Product_ID, int C_BPartner_ID, BigDecimal qty, boolean isSOTrx, String trxName) {
		super.setInitialValues(M_Product_ID, C_BPartner_ID, qty, isSOTrx, trxName);
		checkVendorBreak();
	}
	
	private void checkVendorBreak() {
		int thereAreVendorBreakRecords = DB.getSQLValue(trxName, 
				"SELECT Count(M_Product_ID) FROM M_ProductPriceVendorBreak WHERE M_Product_ID=? AND (C_BPartner_ID=? OR C_BPartner_ID is NULL)",
				m_M_Product_ID, m_C_BPartner_ID);
		m_useVendorBreak = thereAreVendorBreakRecords > 0;
	}
	
	/**
	 * 	Calculate Price
	 * 	@return true if calculated
	 */
	public boolean calculatePrice () {
		if (m_M_Product_ID == 0 
			|| (m_found != null && !m_found.booleanValue()))	//	previously not found
			return false;
		
		if (m_useVendorBreak) {
			//	Price List Version known - vendor break
			if (!m_calculated) {
				m_calculated = calculatePLV_VB ();
				if (m_calculated)
					m_vendorbreak = true;
			}
			//	Price List known - vendor break
			if (!m_calculated) {
				m_calculated = calculatePL_VB();
				if (m_calculated)
					m_vendorbreak = true;
			}
			//	Base Price List used - vendor break
			if (!m_calculated) {
				m_calculated = calculateBPL_VB();
				if (m_calculated)
					m_vendorbreak = true;
			}
		}
		
		//	Price List Version known
		if (!m_calculated)
			m_calculated = calculatePLV ();
		//	Price List known
		if (!m_calculated)
			m_calculated = calculatePL();
		//	Base Price List used
		if (!m_calculated)
			m_calculated = calculateBPL();
		//	Set UOM, Prod.Category
		if (!m_calculated)
			setBaseInfo();
		//	User based Discount
		if (m_calculated && !m_vendorbreak)
			calculateDiscount();
		setPrecision();		//	from Price List
		//
		m_found = Boolean.valueOf(m_calculated);
		return m_calculated;
	}	//	calculatePrice
	
	/**************************************************************************
	 * 	Calculate (Business Partner) Discount
	 */
	private void calculateDiscount() {
		m_discountSchema = false;
		if (m_C_BPartner_ID == 0 || m_M_Product_ID == 0)
			return;
		
		int M_DiscountSchema_ID = 0;
		BigDecimal FlatDiscount = null;
		String sql = "SELECT COALESCE(p.M_DiscountSchema_ID,g.M_DiscountSchema_ID),"
			+ " COALESCE(p.PO_DiscountSchema_ID,g.PO_DiscountSchema_ID), p.FlatDiscount "
			+ "FROM C_BPartner p"
			+ " INNER JOIN C_BP_Group g ON (p.C_BP_Group_ID=g.C_BP_Group_ID) "
			+ "WHERE p.C_BPartner_ID=?";
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement (sql, trxName);
			pstmt.setInt (1, m_C_BPartner_ID);
			rs = pstmt.executeQuery ();
			if (rs.next ())
			{
				M_DiscountSchema_ID = rs.getInt(m_isSOTrx ? 1 : 2);
				FlatDiscount = rs.getBigDecimal(3);
				if (FlatDiscount == null)
					FlatDiscount = Env.ZERO;
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		//	No Discount Schema
		if (M_DiscountSchema_ID == 0)
			return;
		
		IVS_MDiscountSchema ivSD = new IVS_MDiscountSchema(Env.getCtx(), M_DiscountSchema_ID, trxName);
		if (ivSD.get_ID() == 0 || (IVS_MDiscountSchema.DISCOUNTTYPE_Breaks.equals(ivSD.getDiscountType()) && !IVS_MDiscountSchema.CUMULATIVELEVEL_Line.equals(ivSD.getCumulativeLevel())))
			return;
		//
		m_discountSchema = true;
		m_PriceStd = ivSD.calculatePrice(m_Qty, m_PriceStd, m_M_Product_ID, 
			m_M_Product_Category_ID, FlatDiscount, getM_Warehouse_ID(), getM_PriceList_ID());
		
	}	//	calculateDiscount
	
	
	/**
	 * 	Calculate Price based on Price List Version
	 * 	@return true if calculated
	 */
	private boolean calculatePLV()
	{
		if (m_M_Product_ID == 0 || m_M_PriceList_Version_ID == 0)
			return false;
		//
		String sql = "SELECT bomPriceStd(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceStd,"	//	1
			+ " bomPriceList(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceList,"		//	2
			+ " bomPriceLimit(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceLimit,"	//	3
			+ " p.C_UOM_ID,pv.ValidFrom,pl.C_Currency_ID,p.M_Product_Category_ID,"	//	4..7
			+ " pl.EnforcePriceLimit, pl.IsTaxIncluded "	// 8..9
			+ "FROM M_Product p"
			+ " INNER JOIN M_ProductPrice pp ON (p.M_Product_ID=pp.M_Product_ID)"
			+ " INNER JOIN  M_PriceList_Version pv ON (pp.M_PriceList_Version_ID=pv.M_PriceList_Version_ID)"
			+ " INNER JOIN M_Pricelist pl ON (pv.M_PriceList_ID=pl.M_PriceList_ID) "
			+ "WHERE pv.IsActive='Y'"
			+ " AND pp.IsActive='Y'"
			+ " AND p.M_Product_ID=?"				//	#1
			+ " AND pv.M_PriceList_Version_ID=?";	//	#2
		m_calculated = false;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, trxName);
			pstmt.setInt(1, m_M_Product_ID);
			pstmt.setInt(2, m_M_PriceList_Version_ID);
			rs = pstmt.executeQuery();
			if (rs.next())
			{
				//	Prices
				m_PriceStd = rs.getBigDecimal(1);
				if (rs.wasNull())
					m_PriceStd = Env.ZERO;
				m_PriceList = rs.getBigDecimal(2);
				if (rs.wasNull())
					m_PriceList = Env.ZERO;
				m_PriceLimit = rs.getBigDecimal(3);
				if (rs.wasNull())
					m_PriceLimit = Env.ZERO;
				//
				m_C_UOM_ID = rs.getInt(4);
				m_C_Currency_ID = rs.getInt(6);
				m_M_Product_Category_ID = rs.getInt(7);
				m_enforcePriceLimit = "Y".equals(rs.getString(8));
				m_isTaxIncluded = "Y".equals(rs.getString(9));
				//
				if (log.isLoggable(Level.FINE)) log.fine("M_PriceList_Version_ID=" + m_M_PriceList_Version_ID + " - " + m_PriceStd);
				m_calculated = true;
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e); 
			m_calculated = false;
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		return m_calculated;
	}	//	calculatePLV

	/**
	 * 	Calculate Price based on Price List
	 * 	@return true if calculated
	 */
	private boolean calculatePL()
	{
		if (m_M_Product_ID == 0)
			return false;

		//	Get Price List
		/**
		if (m_M_PriceList_ID == 0)
		{
			String sql = "SELECT M_PriceList_ID, IsTaxIncluded "
				+ "FROM M_PriceList pl"
				+ " INNER JOIN M_Product p ON (pl.AD_Client_ID=p.AD_Client_ID) "
				+ "WHERE M_Product_ID=? "
				+ "ORDER BY IsDefault DESC";
			PreparedStatement pstmt = null;
			try
			{
				pstmt = DB.prepareStatement(sql);
				pstmt.setInt(1, m_M_Product_ID);
				ResultSet rs = pstmt.executeQuery();
				if (rs.next())
				{
					m_M_PriceList_ID = rs.getInt(1);
					m_isTaxIncluded = "Y".equals(rs.getString(2));
				}
				rs.close();
				pstmt.close();
				pstmt = null;
			}
			catch (Exception e)
			{
				log.log(Level.SEVERE, "calculatePL (PL)", e);
			}
			finally
			{
				try
				{
					if (pstmt != null)
						pstmt.close ();
				}
				catch (Exception e)
				{}
				pstmt = null;
			}
		}
		/** **/
		if (m_M_PriceList_ID == 0)
		{
			log.log(Level.SEVERE, "No PriceList");
			Trace.printStack();
			return false;
		}

		//	Get Prices for Price List
		String sql = "SELECT bomPriceStd(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceStd,"	//	1
			+ " bomPriceList(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceList,"		//	2
			+ " bomPriceLimit(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceLimit,"	//	3
			+ " p.C_UOM_ID,pv.ValidFrom,pl.C_Currency_ID,p.M_Product_Category_ID,pl.EnforcePriceLimit "	// 4..8
			+ "FROM M_Product p"
			+ " INNER JOIN M_ProductPrice pp ON (p.M_Product_ID=pp.M_Product_ID)"
			+ " INNER JOIN  M_PriceList_Version pv ON (pp.M_PriceList_Version_ID=pv.M_PriceList_Version_ID)"
			+ " INNER JOIN M_Pricelist pl ON (pv.M_PriceList_ID=pl.M_PriceList_ID) "
			+ "WHERE pv.IsActive='Y'"
			+ " AND pp.IsActive='Y'"
			+ " AND p.M_Product_ID=?"				//	#1
			+ " AND pv.M_PriceList_ID=?"			//	#2
			+ " ORDER BY pv.ValidFrom DESC";
		m_calculated = false;
		if (m_PriceDate == null)
			m_PriceDate = new Timestamp (System.currentTimeMillis());
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, trxName);
			pstmt.setInt(1, m_M_Product_ID);
			pstmt.setInt(2, m_M_PriceList_ID);
			rs = pstmt.executeQuery();
			while (!m_calculated && rs.next())
			{
				Timestamp plDate = rs.getTimestamp(5);
				//	we have the price list
				//	if order date is after or equal PriceList validFrom
				if (plDate == null || !m_PriceDate.before(plDate))
				{
					//	Prices
					m_PriceStd = rs.getBigDecimal (1);
					if (rs.wasNull ())
						m_PriceStd = Env.ZERO;
					m_PriceList = rs.getBigDecimal (2);
					if (rs.wasNull ())
						m_PriceList = Env.ZERO;
					m_PriceLimit = rs.getBigDecimal (3);
					if (rs.wasNull ())
						m_PriceLimit = Env.ZERO;
						//
					m_C_UOM_ID = rs.getInt (4);
					m_C_Currency_ID = rs.getInt (6);
					m_M_Product_Category_ID = rs.getInt(7);
					m_enforcePriceLimit = "Y".equals(rs.getString(8));
					//
					if (log.isLoggable(Level.FINE)) log.fine("M_PriceList_ID=" + m_M_PriceList_ID 
						+ "(" + plDate + ")" + " - " + m_PriceStd);
					m_calculated = true;
					break;
				}
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
			m_calculated = false;
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		if (!m_calculated)
			log.finer("Not found (PL)");
		return m_calculated;
	}	//	calculatePL

	/**
	 * 	Calculate Price based on Base Price List
	 * 	@return true if calculated
	 */
	private boolean calculateBPL()
	{
		if (m_M_Product_ID == 0 || m_M_PriceList_ID == 0)
			return false;
		//
		String sql = "SELECT bomPriceStd(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceStd,"	//	1
			+ " bomPriceList(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceList,"		//	2
			+ " bomPriceLimit(p.M_Product_ID,pv.M_PriceList_Version_ID) AS PriceLimit,"	//	3
			+ " p.C_UOM_ID,pv.ValidFrom,pl.C_Currency_ID,p.M_Product_Category_ID,"	//	4..7
			+ " pl.EnforcePriceLimit, pl.IsTaxIncluded "	// 8..9
			+ "FROM M_Product p"
			+ " INNER JOIN M_ProductPrice pp ON (p.M_Product_ID=pp.M_Product_ID)"
			+ " INNER JOIN  M_PriceList_Version pv ON (pp.M_PriceList_Version_ID=pv.M_PriceList_Version_ID)"
			+ " INNER JOIN M_Pricelist bpl ON (pv.M_PriceList_ID=bpl.M_PriceList_ID)"
			+ " INNER JOIN M_Pricelist pl ON (bpl.M_PriceList_ID=pl.BasePriceList_ID) "
			+ "WHERE pv.IsActive='Y'"
			+ " AND pp.IsActive='Y'"
			+ " AND p.M_Product_ID=?"				//	#1
			+ " AND pl.M_PriceList_ID=?"			//	#2
			+ " ORDER BY pv.ValidFrom DESC";
		m_calculated = false;
		if (m_PriceDate == null)
			m_PriceDate = new Timestamp (System.currentTimeMillis());
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, trxName);
			pstmt.setInt(1, m_M_Product_ID);
			pstmt.setInt(2, m_M_PriceList_ID);
			rs = pstmt.executeQuery();
			while (!m_calculated && rs.next())
			{
				Timestamp plDate = rs.getTimestamp(5);
				//	we have the price list
				//	if order date is after or equal PriceList validFrom
				if (plDate == null || !m_PriceDate.before(plDate))
				{
					//	Prices
					m_PriceStd = rs.getBigDecimal (1);
					if (rs.wasNull ())
						m_PriceStd = Env.ZERO;
					m_PriceList = rs.getBigDecimal (2);
					if (rs.wasNull ())
						m_PriceList = Env.ZERO;
					m_PriceLimit = rs.getBigDecimal (3);
					if (rs.wasNull ())
						m_PriceLimit = Env.ZERO;
						//
					m_C_UOM_ID = rs.getInt (4);
					m_C_Currency_ID = rs.getInt (6);
					m_M_Product_Category_ID = rs.getInt(7);
					m_enforcePriceLimit = "Y".equals(rs.getString(8));
					m_isTaxIncluded = "Y".equals(rs.getString(9));
					//
					if (log.isLoggable(Level.FINE)) log.fine("M_PriceList_ID=" + m_M_PriceList_ID 
						+ "(" + plDate + ")" + " - " + m_PriceStd);
					m_calculated = true;
					break;
				}
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
			m_calculated = false;
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		if (!m_calculated)
			log.finer("Not found (BPL)");
		return m_calculated;
	}	//	calculateBPL

	/**
	 * 	Calculate Price based on Price List Version and Vendor Break
	 * 	@return true if calculated
	 */
	private boolean calculatePLV_VB()
	{
		if (m_M_Product_ID == 0 || m_M_PriceList_Version_ID == 0)
			return false;
		//
		String sql = "SELECT pp.PriceStd,"	//	1
			+ " pp.PriceList,"		//	2
			+ " pp.PriceLimit,"	//	3
			+ " p.C_UOM_ID,pv.ValidFrom,pl.C_Currency_ID,p.M_Product_Category_ID,"	//	4..7
			+ " pl.EnforcePriceLimit, pl.IsTaxIncluded "	// 8..9
			+ "FROM M_Product p"
			+ " INNER JOIN M_ProductPriceVendorBreak pp ON (p.M_Product_ID=pp.M_Product_ID)"
			+ " INNER JOIN  M_PriceList_Version pv ON (pp.M_PriceList_Version_ID=pv.M_PriceList_Version_ID)"
			+ " INNER JOIN M_Pricelist pl ON (pv.M_PriceList_ID=pl.M_PriceList_ID) "
			+ "WHERE pv.IsActive='Y'"
			+ " AND pp.IsActive='Y'"
			+ " AND p.M_Product_ID=?"				//	#1
			+ " AND pv.M_PriceList_Version_ID=?"	//	#2
			+ " AND (pp.C_BPartner_ID=? OR pp.C_BPartner_ID is NULL)"				//	#3
			+ " AND ?>=pp.BreakValue"				//  #4
			+ " ORDER BY  pp.C_BPartner_ID, BreakValue DESC";
		m_calculated = false;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, trxName);
			pstmt.setInt(1, m_M_Product_ID);
			pstmt.setInt(2, m_M_PriceList_Version_ID);
			pstmt.setInt(3, m_C_BPartner_ID);
			pstmt.setBigDecimal(4, m_Qty);
			rs = pstmt.executeQuery();
			if (rs.next())
			{
				//	Prices
				m_PriceStd = rs.getBigDecimal(1);
				if (rs.wasNull())
					m_PriceStd = Env.ZERO;
				m_PriceList = rs.getBigDecimal(2);
				if (rs.wasNull())
					m_PriceList = Env.ZERO;
				m_PriceLimit = rs.getBigDecimal(3);
				if (rs.wasNull())
					m_PriceLimit = Env.ZERO;
				//
				m_C_UOM_ID = rs.getInt(4);
				m_C_Currency_ID = rs.getInt(6);
				m_M_Product_Category_ID = rs.getInt(7);
				m_enforcePriceLimit = "Y".equals(rs.getString(8));
				m_isTaxIncluded = "Y".equals(rs.getString(9));
				//
				if (log.isLoggable(Level.FINE)) log.fine("M_PriceList_Version_ID=" + m_M_PriceList_Version_ID + " - " + m_PriceStd);
				m_calculated = true;
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e); 
			m_calculated = false;
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		return m_calculated;
	}	//	calculatePLV_VB

	/**
	 * 	Calculate Price based on P rice List and Vendor break
	 * 	@return true if calculated
	 */
	private boolean calculatePL_VB()
	{
		if (m_M_Product_ID == 0)
			return false;

		//	Get Price List
		/**
		if (m_M_PriceList_ID == 0)
		{
			String sql = "SELECT M_PriceList_ID, IsTaxIncluded "
				+ "FROM M_PriceList pl"
				+ " INNER JOIN M_Product p ON (pl.AD_Client_ID=p.AD_Client_ID) "
				+ "WHERE M_Product_ID=? "
				+ "ORDER BY IsDefault DESC";
			PreparedStatement pstmt = null;
			try
			{
				pstmt = DB.prepareStatement(sql);
				pstmt.setInt(1, m_M_Product_ID);
				ResultSet rs = pstmt.executeQuery();
				if (rs.next())
				{
					m_M_PriceList_ID = rs.getInt(1);
					m_isTaxIncluded = "Y".equals(rs.getString(2));
				}
				rs.close();
				pstmt.close();
				pstmt = null;
			}
			catch (Exception e)
			{
				log.log(Level.SEVERE, "calculatePL (PL)", e);
			}
			finally
			{
				try
				{
					if (pstmt != null)
						pstmt.close ();
				}
				catch (Exception e)
				{}
				pstmt = null;
			}
		}
		/** **/
		if (m_M_PriceList_ID == 0)
		{
			log.log(Level.SEVERE, "No PriceList");
			Trace.printStack();
			return false;
		}

		//	Get Prices for Price List
		String sql = "SELECT pp.PriceStd,"	//	1
			+ " pp.PriceList,"		//	2
			+ " pp.PriceLimit,"	//	3
			+ " p.C_UOM_ID,pv.ValidFrom,pl.C_Currency_ID,p.M_Product_Category_ID,pl.EnforcePriceLimit "	// 4..8
			+ "FROM M_Product p"
			+ " INNER JOIN M_ProductPriceVendorBreak pp ON (p.M_Product_ID=pp.M_Product_ID)"
			+ " INNER JOIN  M_PriceList_Version pv ON (pp.M_PriceList_Version_ID=pv.M_PriceList_Version_ID)"
			+ " INNER JOIN M_Pricelist pl ON (pv.M_PriceList_ID=pl.M_PriceList_ID) "
			+ "WHERE pv.IsActive='Y'"
			+ " AND pp.IsActive='Y'"
			+ " AND p.M_Product_ID=?"				//	#1
			+ " AND pv.M_PriceList_ID=?"			//	#2
			+ " AND (pp.C_BPartner_ID=? OR pp.C_BPartner_ID is NULL)"				//	#3
			+ " AND ?>=pp.BreakValue"				//  #4
			+ " ORDER BY pp.C_BPartner_ID, pv.ValidFrom DESC, BreakValue DESC";
		m_calculated = false;
		if (m_PriceDate == null)
			m_PriceDate = new Timestamp (System.currentTimeMillis());
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, trxName);
			pstmt.setInt(1, m_M_Product_ID);
			pstmt.setInt(2, m_M_PriceList_ID);
			pstmt.setInt(3, m_C_BPartner_ID);
			pstmt.setBigDecimal(4, m_Qty);
			rs = pstmt.executeQuery();
			while (!m_calculated && rs.next())
			{
				Timestamp plDate = rs.getTimestamp(5);
				//	we have the price list
				//	if order date is after or equal PriceList validFrom
				if (plDate == null || !m_PriceDate.before(plDate))
				{
					//	Prices
					m_PriceStd = rs.getBigDecimal (1);
					if (rs.wasNull ())
						m_PriceStd = Env.ZERO;
					m_PriceList = rs.getBigDecimal (2);
					if (rs.wasNull ())
						m_PriceList = Env.ZERO;
					m_PriceLimit = rs.getBigDecimal (3);
					if (rs.wasNull ())
						m_PriceLimit = Env.ZERO;
						//
					m_C_UOM_ID = rs.getInt (4);
					m_C_Currency_ID = rs.getInt (6);
					m_M_Product_Category_ID = rs.getInt(7);
					m_enforcePriceLimit = "Y".equals(rs.getString(8));
					//
					if (log.isLoggable(Level.FINE)) log.fine("M_PriceList_ID=" + m_M_PriceList_ID 
						+ "(" + plDate + ")" + " - " + m_PriceStd);
					m_calculated = true;
					break;
				}
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
			m_calculated = false;
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		if (!m_calculated)
			log.finer("Not found (PL)");
		return m_calculated;
	}	//	calculatePL_VB

	/**
	 * 	Calculate Price based on Base Price List and Vendor Break
	 * 	@return true if calculated
	 */
	private boolean calculateBPL_VB()
	{
		if (m_M_Product_ID == 0 || m_M_PriceList_ID == 0)
			return false;
		//
		String sql = "SELECT pp.PriceStd,"	//	1
			+ " pp.PriceList,"		//	2
			+ " pp.PriceLimit,"	//	3
			+ " p.C_UOM_ID,pv.ValidFrom,pl.C_Currency_ID,p.M_Product_Category_ID,"	//	4..7
			+ " pl.EnforcePriceLimit, pl.IsTaxIncluded "	// 8..9
			+ "FROM M_Product p"
			+ " INNER JOIN M_ProductPriceVendorBreak pp ON (p.M_Product_ID=pp.M_Product_ID)"
			+ " INNER JOIN  M_PriceList_Version pv ON (pp.M_PriceList_Version_ID=pv.M_PriceList_Version_ID)"
			+ " INNER JOIN M_Pricelist bpl ON (pv.M_PriceList_ID=bpl.M_PriceList_ID)"
			+ " INNER JOIN M_Pricelist pl ON (bpl.M_PriceList_ID=pl.BasePriceList_ID) "
			+ "WHERE pv.IsActive='Y'"
			+ " AND pp.IsActive='Y'"
			+ " AND p.M_Product_ID=?"				//	#1
			+ " AND pl.M_PriceList_ID=?"			//	#2
			+ " AND (pp.C_BPartner_ID=? OR pp.C_BPartner_ID is NULL)"				//	#3
			+ " AND ?>=pp.BreakValue"				//  #4
			+ " ORDER BY pp.C_BPartner_ID, pv.ValidFrom DESC, BreakValue DESC";
		m_calculated = false;
		if (m_PriceDate == null)
			m_PriceDate = new Timestamp (System.currentTimeMillis());
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, trxName);
			pstmt.setInt(1, m_M_Product_ID);
			pstmt.setInt(2, m_M_PriceList_ID);
			pstmt.setInt(3, m_C_BPartner_ID);
			pstmt.setBigDecimal(4, m_Qty);
			rs = pstmt.executeQuery();
			while (!m_calculated && rs.next())
			{
				Timestamp plDate = rs.getTimestamp(5);
				//	we have the price list
				//	if order date is after or equal PriceList validFrom
				if (plDate == null || !m_PriceDate.before(plDate))
				{
					//	Prices
					m_PriceStd = rs.getBigDecimal (1);
					if (rs.wasNull ())
						m_PriceStd = Env.ZERO;
					m_PriceList = rs.getBigDecimal (2);
					if (rs.wasNull ())
						m_PriceList = Env.ZERO;
					m_PriceLimit = rs.getBigDecimal (3);
					if (rs.wasNull ())
						m_PriceLimit = Env.ZERO;
						//
					m_C_UOM_ID = rs.getInt (4);
					m_C_Currency_ID = rs.getInt (6);
					m_M_Product_Category_ID = rs.getInt(7);
					m_enforcePriceLimit = "Y".equals(rs.getString(8));
					m_isTaxIncluded = "Y".equals(rs.getString(9));
					//
					if (log.isLoggable(Level.FINE)) log.fine("M_PriceList_ID=" + m_M_PriceList_ID 
						+ "(" + plDate + ")" + " - " + m_PriceStd);
					m_calculated = true;
					break;
				}
			}
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, sql, e);
			m_calculated = false;
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		if (!m_calculated)
			log.finer("Not found (BPL)");
		return m_calculated;
	}	//	calculateBPL_VB
	
	/**
	 * 	Set Base Info (UOM)
	 */
	private void setBaseInfo()
	{
		if (m_M_Product_ID == 0)
			return;
		
		MProduct product = MProduct.get(Env.getCtx(), m_M_Product_ID);
		if (product != null) {
			 m_C_UOM_ID = product.getC_UOM_ID();
			 m_M_Product_Category_ID = product.getM_Product_Category_ID();
		}
		
	}	//	setBaseInfo
	
	/**
	 * 	Set Precision.
	 */
	private void setPrecision ()
	{
		if (m_M_PriceList_ID != 0)
			m_precision = MPriceList.getPricePrecision(Env.getCtx(), getM_PriceList_ID());
	}	//	setPrecision
	
	/**
	 * 	Is Tax Included
	 *	@return tax included
	 */
	public boolean isTaxIncluded()
	{
		return m_isTaxIncluded;
	}	//	isTaxIncluded
	
	/**************************************************************************
	 * 	Calculate Discount Percentage based on Standard/List Price
	 * 	@return Discount
	 */
	public BigDecimal getDiscount()
	{
		BigDecimal Discount = Env.ZERO;
		if (m_PriceList.compareTo(Env.ZERO) != 0)
			Discount = BigDecimal.valueOf((m_PriceList.doubleValue() - m_PriceStd.doubleValue())
				/ m_PriceList.doubleValue() * 100.0);
		if (Discount.scale() > 2)
			Discount = Discount.setScale(2, RoundingMode.HALF_UP);
		return Discount;
	}	//	getDiscount


	
	/**
	 * 	Set PriceList
	 *	@param M_PriceList_ID pl
	 */
	public void setM_PriceList_ID( int M_PriceList_ID)
	{
		super.setM_PriceList_ID(M_PriceList_ID);
		m_calculated = false;
	}	//	setM_PriceList_ID
	
	/**
	 * 	Get PriceList Version
	 *	@return plv
	 */
	public int getM_PriceList_Version_ID()
	{
		return m_M_PriceList_Version_ID;
	}	//	getM_PriceList_Version_ID
	
	/**
	 * 	Set PriceList Version
	 *	@param M_PriceList_Version_ID plv
	 */
	public void setM_PriceList_Version_ID (int M_PriceList_Version_ID)
	{
		super.setM_PriceList_Version_ID(M_PriceList_Version_ID);
		m_calculated = false;
	}	//	setM_PriceList_Version_ID
	
	/**
	 * 	Set Price Date
	 *	@param priceDate date
	 */
	public void setPriceDate(Timestamp priceDate)
	{
		super.setPriceDate(priceDate);
		m_calculated = false;
	}	//	setPriceDate
	
	/**
	 * 	Get Precision
	 *	@return precision - -1 = no rounding
	 */
	public int getPrecision()
	{
		return m_precision;
	}	//	getPrecision
	
	/**
	 * 	Round
	 *	@param bd number
	 *	@return rounded number
	 */
	private BigDecimal round (BigDecimal bd)
	{
		if (m_precision >= 0	//	-1 = no rounding
			&& bd.scale() > m_precision)
			return bd.setScale(m_precision, RoundingMode.HALF_UP);
		return bd;
	}	//	round
	
	/**************************************************************************
	 * 	Get C_UOM_ID
	 *	@return uom
	 */
	public int getC_UOM_ID()
	{
		if (!m_calculated)
			calculatePrice();
		return m_C_UOM_ID;
	}
	
	/**
	 * 	Get Price List
	 *	@return list
	 */
	public BigDecimal getPriceList()
	{
		if (!m_calculated)
			calculatePrice();
		return round(m_PriceList);
	}
	/**
	 * 	Get Price Std
	 *	@return std
	 */
	public BigDecimal getPriceStd()
	{
		if (!m_calculated)
			calculatePrice();
		return round(m_PriceStd);
	}
	/**
	 * 	Get Price Limit
	 *	@return limit
	 */
	public BigDecimal getPriceLimit()
	{
		if (!m_calculated)
			calculatePrice();
		return round(m_PriceLimit);
	}
	/**
	 * 	Get Price List Currency
	 *	@return currency
	 */
	public int getC_Currency_ID()
	{
		if (!m_calculated)
			calculatePrice();
		return m_C_Currency_ID;
	}
	/**
	 * 	Is Price List enforded?
	 *	@return enforce limit
	 */
	public boolean isEnforcePriceLimit()
	{
		if (!m_calculated)
			calculatePrice();
		return m_enforcePriceLimit;
	}	//	isEnforcePriceLimit

	/**
	 * 	Is a DiscountSchema active?
	 *	@return active Discount Schema
	 */
	public boolean isDiscountSchema()
	{
		return m_discountSchema || m_useVendorBreak;	
	}	//	isDiscountSchema
	
	/**
	 * 	Is the Price Calculated (i.e. found)?
	 *	@return calculated
	 */
	public boolean isCalculated()
	{
		return m_calculated;
	}	//	isCalculated
	
	@Override
	public void setOrderLine(I_C_OrderLine orderLine, String trxName) {
		super.setOrderLine(orderLine, trxName);
		if(orderLine != null) {
			I_C_Order order = orderLine.getC_Order();
			setM_Warehouse_ID(order.getM_Warehouse_ID());
		}
		checkVendorBreak();
	}
	
	@Override
	public void setInvoiceLine(I_C_InvoiceLine invoiceLine, String trxName) {
		super.setInvoiceLine(invoiceLine, trxName);
		if(invoiceLine != null) {
			I_C_OrderLine orderLine = invoiceLine.getC_OrderLine();
			if(orderLine != null) {
				I_C_Order order = orderLine.getC_Order();
				setM_Warehouse_ID(order.getM_Warehouse_ID());
			}
		}
		checkVendorBreak();
	}
	
	@Override
	public void setProjectLine(I_C_ProjectLine projectLine, String trxName) {
		super.setProjectLine(projectLine, trxName);
		checkVendorBreak();
	}
	
	@Override
	public void setRequisitionLine(I_M_RequisitionLine reqLine, String trxName) {
		super.setRequisitionLine(reqLine, trxName);
		checkVendorBreak();
	}
	
	@Override
	public void setRMALine(I_M_RMALine rmaLine, String trxName) {
		super.setRMALine(rmaLine, trxName);
		checkVendorBreak();
	}

	/**
	 * Get M_Warehouse_ID
	 * @return the M_Warehouse_ID
	 */
	public int getM_Warehouse_ID() {
		return m_M_Warehouse_ID;
	}

	/**
	 * Set M_Warehouse_ID
	 * @param m_Warehouse_ID the m_Warehouse_ID to set
	 */
	public void setM_Warehouse_ID(int M_Warehouse_ID) {
		this.m_M_Warehouse_ID = M_Warehouse_ID;
	}

}
