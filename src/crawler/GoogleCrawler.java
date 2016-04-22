package crawler;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;



public class GoogleCrawler implements ProgressListener{

	protected Shell shell;
	private Text txtInputUrl;
	private Browser browser;
	private Button btnStart;
	private Button cbAutoExport;
	private List lstResult;
	
	private CrawlerThread mTask = null;
	private Button btnRetry;

	private Display mDisplay;

	public static int sFileCount = 0;
	
	public String mFilePath;
	public String mLogPath;
	
	public boolean mCleared = false;

	public interface CrawlerListener {
		void notifyNew(String s);
		void notifyNew(String s, boolean needAlert);
	}
	
	GoogleCrawler() {
		String path = this.getClass().getResource("/").getPath();
		File f = new File(path); 
		mLogPath = f.getAbsolutePath() + "\\log\\";
		mFilePath = f.getAbsolutePath() + "\\baobei\\";
		File log = new File(mLogPath);
		if (log.exists() && log.isFile()) {
			log.delete();
			log.mkdir();
		} else if (!log.exists()) {
			log.mkdir();
		}
		
		File baobei = new File(mFilePath);
		if (baobei.exists() && baobei.isFile()) {
			baobei.delete();
			baobei.mkdir();
		} else if (!baobei.exists()) {
			baobei.mkdir();
		}
	}
	
	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			GoogleCrawler window = new GoogleCrawler();
			window.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Open the window.
	 */
	public void open() {
		mDisplay = Display.getDefault();
		createContents();
		shell.open();
		shell.layout();
		shell.setLocation(Display.getCurrent().getClientArea().width / 2 - shell.getShell().getSize().x/2, Display.getCurrent()  
                .getClientArea().height / 2 - shell.getSize().y/2); 
		
		while (!shell.isDisposed()) {
			if (!mDisplay.readAndDispatch()) {
				mDisplay.sleep();
			}
		}
	}
	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		shell = new Shell(mDisplay,SWT.CLOSE | SWT.MIN);
		shell.setSize(1024, 690);
		shell.setText("TBCrawler");
		FormLayout layout = new FormLayout();
		shell.setLayout(layout);
		
		txtInputUrl = new Text(shell, SWT.BORDER | SWT.WRAP | SWT.SINGLE);
		txtInputUrl.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				if (!mCleared) {
					txtInputUrl.setText("");
					mCleared = true;
				}
			}
		});
		FormData fd_txtInputUrl = new FormData();
		fd_txtInputUrl.bottom = new FormAttachment(0, 37);
		fd_txtInputUrl.right = new FormAttachment(70, 0);
		fd_txtInputUrl.top = new FormAttachment(0, 10);
		fd_txtInputUrl.left = new FormAttachment(0, 10);
		txtInputUrl.setLayoutData(fd_txtInputUrl);
		txtInputUrl.setText("在此处输入淘宝店铺链接");
		txtInputUrl.setToolTipText("在此处输入淘宝店铺链接");

		cbAutoExport = new Button(shell, SWT.FLAT | SWT.CHECK);
		cbAutoExport.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (mTask != null) {
					mTask.mAutoExport = cbAutoExport.getSelection();
				}
			}
		});
		FormData fd_cbAutoExport = new FormData();
		fd_cbAutoExport.top = new FormAttachment(txtInputUrl, 10);
		fd_cbAutoExport.left = new FormAttachment(txtInputUrl, 0, SWT.LEFT);
		fd_cbAutoExport.bottom = new FormAttachment(txtInputUrl, 56);
		fd_cbAutoExport.right = new FormAttachment(txtInputUrl, 90, SWT.LEFT);
		cbAutoExport.setLayoutData(fd_cbAutoExport);
		
		cbAutoExport.setText("自动导出结果");
		
		browser = new Browser(shell, SWT.BORDER);
		FormData fd_browser = new FormData();
		fd_browser.bottom = new FormAttachment(100, -24);
		fd_browser.top = new FormAttachment(cbAutoExport, 10);
		fd_browser.left = new FormAttachment(txtInputUrl, 0, SWT.LEFT);
		fd_browser.right = new FormAttachment(txtInputUrl, 0, SWT.RIGHT);
		browser.setLayoutData(fd_browser);
		browser.addProgressListener(this);
		
		btnStart = new Button(shell, SWT.NONE);
		FormData fd_btnStart = new FormData();
		fd_btnStart.top = new FormAttachment(txtInputUrl, 0, SWT.TOP);
		fd_btnStart.bottom = new FormAttachment(txtInputUrl, 0, SWT.BOTTOM);
		fd_btnStart.left = new FormAttachment(txtInputUrl, 20, SWT.RIGHT);
		fd_btnStart.right = new FormAttachment(txtInputUrl, 150, SWT.RIGHT);
		btnStart.setLayoutData(fd_btnStart);
		btnStart.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				crawleStart();
			}
		});
		btnStart.setText("开始抓取");
		
		btnRetry = new Button(shell, SWT.NONE);
		FormData fd_btnRetry = new FormData();
		fd_btnRetry.top = new FormAttachment(txtInputUrl, 0, SWT.TOP);
		fd_btnRetry.bottom = new FormAttachment(txtInputUrl, 0, SWT.BOTTOM);
		fd_btnRetry.left = new FormAttachment(btnStart, 10, SWT.RIGHT);
		fd_btnRetry.right = new FormAttachment(btnStart, 150, SWT.RIGHT);
		btnRetry.setLayoutData(fd_btnRetry);
		btnRetry.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (mTask != null) {
					mTask.exportResult();
				} else {
					addLogInfo("无法导出数据");
				}
			}
		});
		btnRetry.setText("导出结果");
		
		lstResult = new List(shell, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		FormData fd_lstResult = new FormData();
		fd_lstResult.top = new FormAttachment(browser, 0, SWT.TOP);
		fd_lstResult.left = new FormAttachment(browser, 20);
		fd_lstResult.bottom = new FormAttachment(browser, 0, SWT.BOTTOM);
		fd_lstResult.right = new FormAttachment(100, -10);
		lstResult.setLayoutData(fd_lstResult);
		addLogInfo("任务详情：");
	}
	
	public void crawleStart(){
		lstResult.removeAll();
		addLogInfo("任务详情：");
		
		String url = getUrl();
		if (url.length() == 0) {
			return ;
		}
		mTask = new CrawlerThread(url, mFilePath, new CrawlerListener() {

			@Override
			public void notifyNew(final String s) {
				notifyNew(s, false);
			}

			@Override
			public void notifyNew(final String s, boolean needAlert) {
				lstResult.getDisplay().asyncExec(new Runnable() {

					@Override
					public void run() {
						addLogInfo(s);
					}
					
				});
				if (needAlert) {
					MessageBox box = new MessageBox(shell, SWT.OK);
					box.setMessage(s);
					box.open();
				}
			}
			
		});
		mTask.mAutoExport = cbAutoExport.getSelection();
		String searchUrl = mTask.getSearchUrl();
		addLogInfo("获取到店铺宝贝地址：" + searchUrl);
		browser.setData("GetRealUrl", true);
		txtInputUrl.setText(searchUrl);
		browser.setUrl(searchUrl);
	}
	
	private void savePage(String filename, String content) {
		try {
			FileWriter f = new FileWriter(new File(mLogPath + filename));
			f.write(content);
			f.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private String getUrl() {
		String url = txtInputUrl.getText();
		URI uri = URI.create(url);
		if (!url.contains(".taobao.com") || url.contains("www.taobao.com")) {
			addLogInfo("输入的网址有错误:" + url);
			return "";
		}
		try {
			URI newUri = new URI(uri.getScheme(), uri.getHost(), null, null);
			return newUri.toString();
		} catch (URISyntaxException e) {
			addLogInfo(e.toString());
		}
		return uri.getScheme() + "//" + uri.getHost();
	}

	private void addLogInfo(String line) {
		lstResult.add(line);
		ScrollBar sb = lstResult.getVerticalBar();
		if(sb != null && sb.isVisible()){
			System.out.println(sb.getMaximum());
			sb.setSelection(sb.getMaximum());
			lstResult.select(lstResult.getItemCount() - 1);
			lstResult.showSelection();
		} 
	}
	
	@Override
	public void changed(ProgressEvent event) {
		
	}

	@Override
	public void completed(ProgressEvent event) {
		addLogInfo("网页获取成功：" + browser.getUrl());
		final String content = browser.getText();
		savePage(++sFileCount + ".htm", content);
		if ((Boolean)browser.getData("GetRealUrl")) {//获取异步URL，通过这个URL可以遍历宝贝
			browser.setData("GetRealUrl", false);
			final String realUrl = mTask.analyzeRealUrl(content);
			browser.getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					if (browser.isDisposed() || txtInputUrl.isDisposed()) {
						return ;
					}
					txtInputUrl.setText(realUrl);
					browser.stop();
					browser.setUrl(realUrl);
				}
			});
		} else {
			browser.stop();
			if (mTask.hasNextPage()) {
				browser.getDisplay().asyncExec(new Runnable() {

					@Override
					public void run() {
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
						}
						mTask.analyzeRealPage(content);
						browser.setUrl(mTask.getNextPageUrl(browser.getUrl()));
					}
					
				});
			}
		}
	}
}
