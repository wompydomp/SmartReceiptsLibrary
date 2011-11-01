package wb.receiptspro;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.text.DecimalFormat;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.text.format.DateFormat;
import android.util.Log;

import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Image;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfPageEventHelper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.Element;

import wb.android.sdcard.SDCardFileManager;

public class EmailAttachmentWriter extends AsyncTask<TripRow, Integer, Long>{

	private static final String TAG = "EmailAttachmentWriter";
	
	private final SmartReceiptsActivity _activity;
	private final SDCardFileManager _sdCard;
	private final DatabaseHelper _db;
	private final ProgressDialog _dialog;
	private final boolean _fullPDF, _imgPDF, _csv;
	private final File[] _files;

	private static final String IMAGES_PDF = "Images.pdf";
	private static final String FOOTER = "Report Generated using Smart Receipts for Android";
	
	static final int FULL_PDF = 0;
	static final int IMG_PDF = 1;
	static final int CSV = 2;
	
	public EmailAttachmentWriter(SmartReceiptsActivity activity, SDCardFileManager sdCard, DatabaseHelper db, ProgressDialog dialog, boolean buildFullPDF, boolean buildImagesPDF, boolean buildCSV) {
		_activity = activity;
		_sdCard = sdCard;
		_db = db;
		_dialog = dialog;
		_fullPDF = buildFullPDF;
		_imgPDF = buildImagesPDF;
		_csv = buildCSV;
		_files = new File[] {null, null, null};
	}
	
	@Override
	protected Long doInBackground(TripRow... trips) {
		if (trips.length == 0)
			return new Long(100L); //Should never be reached
		final TripRow trip = trips[0];
		final ReceiptRow[] receipts = _db.getReceipts(trip, false);
		final int len = receipts.length;
		if (_fullPDF) {
			try {
				final FileOutputStream pdf = _sdCard.getFOS(trip.dir, trip.dir.getName() + ".pdf");
	            Document document = new Document();
				PdfWriter writer = PdfWriter.getInstance(document, pdf);
				writer.setPageEvent(new Footer());
				document.open();
				BigDecimal amnt = new BigDecimal(trip.price);
				document.add(new Paragraph(SmartReceiptsActivity.CURRENCY_FORMAT.format(amnt.doubleValue()) + "  \u2022  " + trip.dir.getName() + "\n"
							+ "From: " + DateFormat.getDateFormat(_activity).format(trip.from) + " To: " + DateFormat.getDateFormat(_activity).format(trip.to) + "\n\n\n"));
				PdfPTable table = new PdfPTable(6);
				table.setWidthPercentage(100);
				ReceiptRow receipt;
				for (int i=0; i < len; i++) {
					receipt = receipts[i];
					amnt = new BigDecimal(receipt.price);
					table.addCell(receipt.name);
					table.addCell(SmartReceiptsActivity.CURRENCY_FORMAT.format(amnt.doubleValue()));
					table.addCell(DateFormat.getDateFormat(_activity).format(receipt.date));
					table.addCell(receipt.category);
					table.addCell(receipt.comment);
					table.addCell(((receipt.expensable)?"":"Not ") + "Expensable");
				}
				document.add(table);
				document.newPage();
				this.addImageRows(document, receipts);
				document.close();
				_files[FULL_PDF] = _sdCard.getFile(trip.dir, trip.dir.getName() + ".pdf");
			} catch (FileNotFoundException e) {
				Log.e(TAG, e.toString());
			} catch (DocumentException e) {
				Log.e(TAG, e.toString());
			}
		}
		if (_imgPDF) {
			try {
				final FileOutputStream pdf = _sdCard.getFOS(trip.dir, trip.dir.getName() + IMAGES_PDF);
	            Document document = new Document();
				PdfWriter writer = PdfWriter.getInstance(document, pdf);
				writer.setPageEvent(new Footer());
				document.open();
				this.addImageRows(document, receipts);
				document.close();
				_files[IMG_PDF] = _sdCard.getFile(trip.dir, trip.dir.getName() + IMAGES_PDF);
			} catch (FileNotFoundException e) {
				Log.e(TAG, e.toString());
			} catch (DocumentException e) {
				Log.e(TAG, e.toString());
			}
		}
		if (_csv) {
			ReceiptRow receipt;
			String data = "", date;
			DecimalFormat df = new DecimalFormat();
			df.setMaximumFractionDigits(2);
			df.setMinimumFractionDigits(2);
			for (int i=0; i < len; i++) {
				receipt = receipts[i];
				date = DateFormat.getDateFormat(_activity).format(receipt.date);
				data += _db.getCategoryCode(receipt.category) + "," + receipt.name + "," + df.format(new Float(receipt.price)) + "," + date + "\n";
			}
			String filename = trip.dir.getName() + ".csv";
			if (!_sdCard.write(trip.dir, filename, data))
				Log.e(TAG, "Failed to write the csv file");
			_files[CSV] = _sdCard.getFile(trip.dir, filename);
		}
		return new Long(100L);
	}
	
	private Document addImageRows(Document document, ReceiptRow[] receipts) {
			PdfPTable table = new PdfPTable(2);
			table.getDefaultCell().disableBorderSide(PdfPCell.LEFT);
			table.getDefaultCell().disableBorderSide(PdfPCell.RIGHT);
			table.getDefaultCell().disableBorderSide(PdfPCell.TOP);
			table.getDefaultCell().disableBorderSide(PdfPCell.BOTTOM);
			table.setWidthPercentage(100);
			table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_CENTER);
			table.getDefaultCell().setVerticalAlignment(Element.ALIGN_CENTER);
			table.setSplitLate(false);
			final int size = receipts.length;
			ReceiptRow receipt;
			Image img1 = null, img2 = null;
			ReceiptRow receipt1 = null;
			int num1 = 0, flag = 0;
			for (int i=0; i < size; i++) {
				receipt = receipts[i]; 
				if (receipt.img != null && img1 == null) {
					try {
						img1 = Image.getInstance(receipt.img.getCanonicalPath());
					} catch (BadElementException e) {
						Log.e(TAG, e.toString());
						continue;
					} catch (MalformedURLException e) {
						Log.e(TAG, e.toString());
						continue;
					} catch (IOException e) {
						Log.e(TAG, e.toString());
						continue;
					}
					receipt1 = receipt;
					num1 = i;
				}
				else if (receipt.img != null && img2 == null) {
					try {
						img2 = Image.getInstance(receipt.img.getCanonicalPath());
					} catch (BadElementException e) {
						Log.e(TAG, e.toString());
						continue;
					} catch (MalformedURLException e) {
						Log.e(TAG, e.toString());
						continue;
					} catch (IOException e) {
						Log.e(TAG, e.toString());
						continue;
					}
					table.addCell((num1+1) + "  \u2022  " + receipt1.name + "  \u2022  " + DateFormat.getDateFormat(_activity).format(receipt1.date));
					table.addCell((i+1) + "  \u2022  " + receipt.name + "  \u2022  " + DateFormat.getDateFormat(_activity).format(receipt.date));
					table.addCell(img1);
					table.addCell(img2);
					table.setSpacingAfter(40);
					img1 = null; img2 = null;
					if (++flag==2) {//ugly hack to fix how page breaks are separated
						table.completeRow();
						try {
							document.add(table);
						} catch (DocumentException e) {
							Log.e(TAG, e.toString());
						}
						table = new PdfPTable(2);
						table.getDefaultCell().disableBorderSide(PdfPCell.LEFT);
						table.getDefaultCell().disableBorderSide(PdfPCell.RIGHT);
						table.getDefaultCell().disableBorderSide(PdfPCell.TOP);
						table.getDefaultCell().disableBorderSide(PdfPCell.BOTTOM);
						table.setWidthPercentage(100);
						table.getDefaultCell().setHorizontalAlignment(Element.ALIGN_CENTER);
						table.getDefaultCell().setVerticalAlignment(Element.ALIGN_CENTER);
						table.setSplitLate(false);
						flag = 0;
					}
				}
			}
			if (img1 != null) {
				table.addCell((num1+1) + "  \u2022  " + receipt1.name + "  \u2022  " + DateFormat.getDateFormat(_activity).format(receipt1.date));
				table.addCell(" ");
				table.addCell(new PdfPCell(img1, true));
			}
			table.completeRow();
			try {
				document.add(table);
			} catch (DocumentException e) {
				Log.e(TAG, e.toString());
			}
			return document;
	}
	
	@Override
	protected void onPostExecute(Long result) {
		_activity.postCreateAttachments(_files);
		_dialog.cancel();
	}
	
	private class Footer extends PdfPageEventHelper {
		public void onEndPage(PdfWriter writer, Document document) {
			Rectangle rect = writer.getPageSize();
			ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT, 
					new Phrase(FOOTER, FontFactory.getFont("Times-Roman", 9, Font.ITALIC)), rect.getLeft()+36, rect.getBottom()+36, 0);
		}
	}

}