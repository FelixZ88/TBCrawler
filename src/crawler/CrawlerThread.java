package crawler;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.filters.OrFilter;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.tags.DefinitionListBullet;
import org.htmlparser.tags.InputTag;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.tags.Span;
import org.htmlparser.util.NodeIterator;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import crawler.GoogleCrawler.CrawlerListener;


public class CrawlerThread {
	private String mEncoding = "UTF-8";
	
	private String mRealUrl;
	private String mShopName;
	private URI mUri;

	List<TBItem> mResults = new ArrayList<TBItem>();
	
	boolean mHasNextPage = true;
	
	boolean mCrawling = false;
	boolean mCrawled = false;
	
	private String mResultPath;
	public boolean mAutoExport = false;
	private CrawlerListener mListener;
	
	public CrawlerThread(String orignalUrl, String resultPath, CrawlerListener progress) {
		mRealUrl = orignalUrl;
		mUri = URI.create(mRealUrl);
		
		String host = mUri.getHost();
		mShopName = host.substring(0, host.indexOf("."));
		mResultPath = resultPath + mShopName + ".xls";
		mListener = progress;
	}
	
	public String getSearchUrl() {
		return mRealUrl + "/search.htm";
	}
	
	private Node travalHtmlTree(Node node, String keyword) {
		return travalHtmlTree(node, keyword, false);
	}
	
	private Node travalHtmlTree(Node node, String keyword, boolean print) {
		if (node.getText().contains(keyword)) {
			if (print) {
				System.out.print(node.getText());
			}
			return node;
		}
			
		NodeList nl = node.getChildren();
		if (nl == null) {
			return null;
		}
		Node retNode = null;
		for (int i = 0; i < nl.size(); i ++) {
			retNode = travalHtmlTree(nl.elementAt(i), keyword, print);
			if (retNode != null) {
				break;
			}
		}
		return retNode;
	}
	
	/**
	 * ��ʵ�����ݣ�������ȡ���Ա�����
	 * @param content
	 * @return
	 */
	public void analyzeRealPage(String content) {
		Parser p = Parser.createParser(content, "UTF-8");
		analyzeDetailNode(p);
		if (!mHasNextPage) {
			mCrawling = false;
			mCrawled = true;
			if (mAutoExport) {
				exportResult();
			}
		}
	}
	
	public String getNextPageUrl(String curUrl) {
		URI uri = URI.create(curUrl);
		
		String query = uri.getQuery();
		
		int pageNo = 0;
		ArrayList<Pair<String, String>> kvs = new ArrayList<Pair<String, String>>();
		String[] querys = query.split("&");
		boolean hasPageNo = false;
		if (querys != null && querys.length > 0) {
			for (String q : querys) {
				String[] kv = q.split("=");
				if (kv[0].equals("pageNo")) {
					pageNo = Integer.parseInt(kv[1]);
					pageNo ++;
					kv[1] = String.valueOf(pageNo);
					hasPageNo = true;
				}
				kvs.add(Pair.create(kv[0], kv[1]));
			}
		}
		
		if (!hasPageNo) {
			kvs.add(Pair.create("pageNo", "2"));
		}
		
		StringBuffer sb = new StringBuffer();
		for (int i = 0 ; i < kvs.size(); i ++) {
			Pair<String, String> kv = kvs.get(i);
			sb.append(kv.first);
			sb.append("=");
			sb.append(kv.second);
			sb.append("&");
		}
		
		String newQuery = "";
		if (sb.length() > 2) {
			newQuery = sb.substring(0, sb.length() - 1);
		}
		try {
			URI nextURI = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), newQuery, uri.getFragment());
			return nextURI.toString();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return curUrl + "&pageNo=2";
	}
	
//	private void analyzeItemCount(Parser p) {
//		try {
//			NodeIterator ni = p.elements();
//			while(ni.hasMoreNodes()) {
//				Node countNode = travalHtmlTree(ni.nextNode(), "pagination");
//				if (countNode != null) {
//					NodeList nl = countNode.getChildren();
//					for (int i = 0; nl != null && i < nl.size(); i ++) {
//						Node n = nl.elementAt(i);
//						if (n instanceof Span) {
//							mListener.notifyNew(n.getText());
//							NodeList spanChild = n.getChildren();
//							if (spanChild!= null && spanChild.size()>0) {
//								String count = spanChild.elementAt(0).getText();
//								try {
//									mPageCount = Integer.parseInt(count.trim());
//									isKnowCount = true;
//								} catch (Exception e) {
//									isKnowCount = false;
//									mPageCount = 0;
//								}
//							}
//							break;
//						}
//					}
//				}
//			}
//		} catch (ParserException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		
//	}
	
	private void analyzeDetailNode(Parser p) {
		try {
			NodeFilter[]  filters = new NodeFilter[2];
			filters[0] = new NodeClassFilter(DefinitionListBullet.class);
			filters[1] = new NodeClassFilter(Span.class);
			NodeFilter of = new OrFilter(filters);
			NodeList nl = p.extractAllNodesThatMatch(of);
			if (nl != null && nl.size() > 0) {
				NodeList newList = new NodeList();
				for (int i = 0; i < nl.size(); i ++) {
					Node n = nl.elementAt(i);
					if (n instanceof DefinitionListBullet) {
						if (n.getText().contains("detail")) {
							newList.add(n);
//							mListener.notifyNew(n.getText());
						}
					}else if (n instanceof Span) {
						if (n.getText().contains("page-info")) {
							NodeList detailList = new NodeList();
							NodeFilter nf2 = new NodeClassFilter(TextNode.class);
							n.collectInto(detailList, nf2);
							for (int k = 0; k < detailList.size(); k ++) {
								TextNode tag = (TextNode)detailList.elementAt(k);
								String pageIndex = tag.getText();
								mListener.notifyNew("��ǰҳ�룺" + pageIndex);
								String[] page = pageIndex.split("/");
								try {
									int cp = Integer.parseInt(page[0]);
									int tp = Integer.parseInt(page[1]);
									if (cp == tp) {
										mHasNextPage = false;
									}
								} catch(Exception e) {
									
								}
								break;
							}
						}
					}
				}
				
				int count = 0; 
				if (newList.size() > 0) {
					for(int j = 0; j < newList.size(); j ++) {
						Node n = newList.elementAt(j);
						NodeList detailList = new NodeList();
						NodeFilter[] nfs = new NodeFilter[2];
						nfs[0] = new NodeClassFilter(LinkTag.class);
						nfs[1] = new NodeClassFilter(Span.class);
						NodeFilter nf4 = new OrFilter(nfs);
						n.collectInto(detailList, nf4);
						TBItem tbItem = new TBItem();
						for (int k = 0; k < detailList.size(); k ++) {
							Node ele = detailList.elementAt(k);
							if (ele instanceof LinkTag) {
								LinkTag tag = (LinkTag)ele;
								tbItem.itemName = tag.getLinkText();
								mListener.notifyNew(tag.getLinkText());
								String link = tag.getLink();
								link = link.replace("\\\"", "");
								tbItem.itemLink = (mUri.getScheme() + ":" + link);
							} else if (ele instanceof Span) {
								Span sEle = (Span) ele;
								if (ele.getText().contains("c-price")) {
									tbItem.itemCPrice = sEle.getStringText();
								} else if (ele.getText().contains("s-price")) {
									tbItem.itemSPrice = sEle.getStringText();
								} else if (ele.getText().contains("sale-num")) {
									tbItem.itemSaleCount = sEle.getStringText();
								}
							}
						}
						if (TBItem.check(tbItem)) {
							count ++;
							mResults.add(tbItem);
						}
					}
					mListener.notifyNew("��ǰҳ�湲��ȡ������������" + count);
				}
				if (count == 0) {
					mHasNextPage = false;
				}
				
			}
		} catch (ParserException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * ����ҳ�������ҵ��첽���Ա�������URL��һ������/i/asynSearch.htm��ʼ��URL����J_ShopAsynSearchURLΪ��ǩ����
	 */
	public String analyzeRealUrl(String content) {
		mCrawling = true;
		Parser p = Parser.createParser(content, "UTF-8");
		try {
			NodeIterator ni =  p.elements();
			Node urlNode = null;
			while(ni.hasMoreNodes()) {
				urlNode = travalHtmlTree(ni.nextNode(), "J_ShopAsynSearchURL");
				if (urlNode != null) {
					break ;
				}
			}
			if (urlNode != null && urlNode instanceof InputTag) {
				String searchUrl = ((InputTag)urlNode).getAttribute("value");
				String realUrl = mRealUrl + searchUrl;
				mListener.notifyNew("��ȡ���������ӣ�" + realUrl);
				return realUrl;
			}
		} catch (ParserException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	private String getString(InputStream is, String encoding)
			throws IOException {
		GZIPInputStream gzIs = new GZIPInputStream(is);
		BufferedReader reader = new BufferedReader(new InputStreamReader(gzIs,
				"UTF-8"));
		StringBuilder sb = new StringBuilder();
		String line = null;
		try {
			while ((line = reader.readLine()) != null) {

				sb.append(line);
			}
		} catch (IOException e) {
			throw e;
		}

		return sb.toString();
	}

	private void connectUrl(String urlString){

		String content = null;
		URL url = null;
		HttpURLConnection httpConn = null;
		InputStream in = null;
		try {
			url = new URL(urlString);
			httpConn = (HttpURLConnection) url.openConnection();
			HttpURLConnection.setFollowRedirects(true);
			httpConn.setRequestMethod("GET");
			httpConn.setRequestProperty("refer", "http://www.google.com.hk/");
			httpConn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
			httpConn.setRequestProperty("Accept-Encoding", "gzip, deflate");
			// httpConn.setRequestProperty("Date",
			// "Fri, 26 Apr 2013 07:40:46 GMT");
			// httpConn.setRequestProperty("Content-Disposition", "attachment");
			// httpConn.setRequestProperty("Transfer-Encoding", "chunked");
			// httpConn.setRequestProperty("X-Cache-Lookup",
			// "MISS from proxy:8080, MISS from proxy:8080");
			// httpConn.setRequestProperty("X-Frame-Options","SAMEORIGIN");
			// httpConn.setRequestProperty("x-xss-protection","1 mode=block");
			httpConn.setRequestProperty("Host", "www.google.com.hk");
			httpConn.setRequestProperty(
					"Cookie",
					"PREF=ID=1982785378454a01:U=5461588633a26e63:FF=2:LD=en:TM=1347850190:LM=1359030423:S=wZiG-gTYjphXfa-B; NID=67=UBCLVUGwzhpOuK0SWgtjhBLTsO7c657xspcbQn8LNPcO-jUvpI06TmnkGK5NsSpnIsjWtptk0H-0TjOEbd6sWtEAwTv-3sk9r8Xk2iCPb1xj-MfzU4h_hEF_NPXxcgZy");
			httpConn.setRequestProperty("Content-Type",
					"application/json; charset=UTF-8");
			httpConn.setRequestProperty("Accept",
					"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
			httpConn.setRequestProperty("User-Agent",
					"Mozilla/5.0 (Windows NT 6.1; rv:18.0) Gecko/20100101 Firefox/18.0");

			int responseCode = httpConn.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				in = httpConn.getInputStream();
				content = getString(in, "utf-8");
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				in.close();
				httpConn.disconnect();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public String getRealUrl() {
		return mRealUrl;
	}

	private boolean isExported = false;
	
	public void exportResult() {
		if (isExported) {
			mListener.notifyNew("�Ѿ�����������������ظ�����", true);
			return ;
		}
		
		if (mCrawling) {
			mListener.notifyNew("����Ѱ�ұ������Ժ��ٵ������", true);
			return ;
		}
		
		if (!mCrawled) {
			mListener.notifyNew("����ûִ�У����޽��", true);
			return ;
		}
		
		if (exportResultXls()) {
			mListener.notifyNew("�ѵ����������" + mResultPath, true);
			isExported = true;
		}else {
			mListener.notifyNew("����ʧ��", true);
		}
	}

	public boolean hasNextPage() {
		return mHasNextPage;
	}
	

	private HSSFWorkbook createExcelHeader() {
		HSSFWorkbook wb = new HSSFWorkbook();
		HSSFSheet sheet = wb.createSheet(mShopName);

		// ����excelÿ�п��
		sheet.setColumnWidth(0, 4000);
		sheet.setColumnWidth(1, 4000);
		sheet.setColumnWidth(2, 4000);
		sheet.setColumnWidth(3, 4000);
		sheet.setColumnWidth(4, 6000);


		// ������Ԫ����ʽ
		HSSFCellStyle style = wb.createCellStyle();
//		style.setAlignment(HSSFCellStyle.ALIGN_CENTER);
//		style.setVerticalAlignment(HSSFCellStyle.VERTICAL_CENTER);
//		style.setFillForegroundColor(HSSFColor.LIGHT_TURQUOISE.index);
//		style.setFillPattern(HSSFCellStyle.SOLID_FOREGROUND);

		// ���ñ߿�
//		style.setBottomBorderColor(HSSFColor.RED.index);
//		style.setBorderBottom(HSSFCellStyle.BORDER_THIN);
//		style.setBorderLeft(HSSFCellStyle.BORDER_THIN);
//		style.setBorderRight(HSSFCellStyle.BORDER_THIN);
//		style.setBorderTop(HSSFCellStyle.BORDER_THIN);


		// ����Excel��sheet��һ��
		HSSFRow row = sheet.createRow(0);
//		row.setHeight((short) 500);// �趨�еĸ߶�
		// ����һ��Excel�ĵ�Ԫ��
		HSSFCell cell = row.createCell(0);

		// �ϲ���Ԫ��(startRow��endRow��startColumn��endColumn)
//		sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

		// ��Excel�ĵ�Ԫ��������ʽ�͸�ֵ
		cell.setCellStyle(style);
		cell.setCellValue("��������");

		// ���õ�Ԫ�����ݸ�ʽ
//		HSSFCellStyle style1 = wb.createCellStyle();
//		style1.setDataFormat(HSSFDataFormat.getBuiltinFormat("h:mm:ss"));

//		style1.setWrapText(true);// �Զ�����

		// ���õ�Ԫ�����ʽ��ʽ

		cell = row.createCell(1);
		cell.setCellStyle(style);
		cell.setCellValue("��������");


		cell = row.createCell(2);
		cell.setCellStyle(style);
		cell.setCellValue("�ۼ�");

		cell = row.createCell(3);
		cell.setCellStyle(style);
		cell.setCellValue("ԭ��");

		cell = row.createCell(4);
		cell.setCellStyle(style);
		cell.setCellValue("����");
		// ����������
//		HSSFHyperlink link = new HSSFHyperlink(HSSFHyperlink.LINK_URL);
//		link.setAddress("http://www.baidu.com");
//		cell = row.createCell(2);
//		cell.setCellValue("�ٶ�");
//		cell.setHyperlink(link);// �趨��Ԫ�������
		return wb;
	}
	
	public boolean exportResultXls() {
		HSSFWorkbook wb = createExcelHeader();
		
		for (int i = 0; i <mResults.size(); i++) {
			TBItem tbItem = mResults.get(i);
			HSSFSheet sheet = wb.getSheet(mShopName);
			
			HSSFRow row = sheet.createRow(i+1);
			HSSFCell cell0 = row.createCell(0);
			cell0.setCellValue(tbItem.itemName);
			
			HSSFCell cell1 = row.createCell(1);
			cell1.setCellValue(tbItem.itemLink);

			HSSFCell cell2 = row.createCell(2);
			cell2.setCellValue(tbItem.itemCPrice);

			HSSFCell cell3 = row.createCell(3);
			cell3.setCellValue(tbItem.itemSPrice);

			HSSFCell cell4 = row.createCell(4);
			cell4.setCellValue(tbItem.itemSaleCount);
		}
		FileOutputStream os = null;
		try {
			os = new FileOutputStream(mResultPath);
			wb.write(os);
			os.close();
			os = null;
			return true;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
//			result = "�ļ�·����������";
		} catch (IOException e) {
			e.printStackTrace();
//			result = "�ļ�д��ʧ�ܣ�1��";
			return false;
		} catch (Exception e) {
			e.printStackTrace();
//			result = "�ļ�д��ʧ�ܣ�1��";
			return false;
		} finally{
			if (os!=null) {
				try {
					os.close();
					os = null;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	static class TBItem {
		String itemName = "";
		String itemLink = "";
		String itemCPrice = "";
		String itemSPrice = "";
		String itemSaleCount = "";
		public static boolean check(TBItem tbItem) {
			if (tbItem != null && 
					tbItem.itemName.length() > 0 &&
					tbItem.itemLink.length() > 0) {
				return true;
			}
			return false;
		}
	}
	
}
