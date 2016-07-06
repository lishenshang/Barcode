package com.sample.barcode;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.sample.barcode.camera.CameraManager;
import com.sample.barcode.decode.Intents;
import com.sample.barcode.encode.Contents;
import com.sample.barcode.view.ViewfinderView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class CaptureActivity extends Activity implements Callback {

	TextView textView;
	SurfaceView surfaceView;
	ViewfinderView viewfinderView;
	CameraManager cameraManager;
	boolean isHasSurface,ishandleDecode;
	CaptureActivityHandler handler;

	private boolean hasSurface;
	private Vector<BarcodeFormat> decodeFormats;
	private String characterSet;
	private InactivityTimer timer;
	
	 final MultiFormatReader multiFormatReader= new MultiFormatReader();
	 
	 Handler myHandler=new Handler(){
		 public void handleMessage(Message msg) {
			 switch (msg.what) {
			case 0:
				String res=(String) msg.obj;
				 handleResult(res);
				break;
			case 1:
				Toast.makeText(CaptureActivity.this, "解码出错", Toast.LENGTH_SHORT).show();
				break;

			default:
				break;
			}
			 
		 };
	 };

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_capture);
		initViews();
		hasSurface = false;
		timer = new InactivityTimer(this);
		cameraManager = new CameraManager(getApplication());
		viewfinderView.setCameraManager(cameraManager);
	}

	private void initViews() {
		textView = (TextView) findViewById(R.id.textView);
		surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		viewfinderView = (ViewfinderView) findViewById(R.id.viewfinderView);
	}
	
	
	@Override
	protected void onResume() {
		super.onResume();
		
		surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (hasSurface) {
			initCamera(surfaceHolder);
		} else {
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}
		decodeFormats = null;
		characterSet = null;
	}
	
	@Override
	  public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater menuInflater = getMenuInflater();
	    menuInflater.inflate(R.menu.capture, menu);
	    return true;
	  }
	
	 @Override
	  public boolean onOptionsItemSelected(MenuItem item) {
		 int id=item.getItemId();
		 switch (id) {
		case R.id.action_qrcode:
			final EditText et=new EditText(this);
			et.setMaxEms(15);
			et.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
			AlertDialog d=new AlertDialog.Builder(this).setView(et).setTitle("请输入二维码信息：").setNegativeButton("取消", null).setNeutralButton("确定", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String text=et.getText().toString();
					if(!text.isEmpty()&&!"".equals(text)&&text!=null)
					{
						launchSearch(Intents.Encode.TYPE, Contents.Type.TEXT, Intents.Encode.DATA, text);
					}
				}
			}).show();
			break;
			
		case R.id.action_local:
			Thread tt=new Thread(){
				@Override
				public void run() {
					Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
					intent.setType("image/*");
					startActivityForResult(intent, 0);
				}
			};
			tt.start();
			break;

		default:
			break;
		}
		return true;
		 
	 }
	 
	 @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode==0&&resultCode==RESULT_OK)
		{
			final Uri imgUri=data.getData();
			Thread t=new Thread(){
				@Override
				public void run() {
					try {
						Bitmap bitmap=MediaStore.Images.Media.getBitmap(getContentResolver(), imgUri);
						decode(bitmap);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
			t.start();
		}
	}
	
	 private void decode(Bitmap bitmap) {
		 try {
			    Result rawResult = null;
			    int width=bitmap.getWidth();
				 int height=bitmap.getHeight();
				 int[] pixels=new int[width*height];
				 bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
					RGBLuminanceSource rgb=new RGBLuminanceSource(width, height, pixels);
					HybridBinarizer hybridBinarizer=new HybridBinarizer(rgb);
					BinaryBitmap bb=new BinaryBitmap(hybridBinarizer);
			      try {
			        rawResult = multiFormatReader.decodeWithState(bb);
			        if(rawResult!=null)
					{
						String res=rawResult.getText();
						Message msg=myHandler.obtainMessage(0, res);
						myHandler.sendMessage(msg);
					}
					else
					{
						Message msg=myHandler.obtainMessage(1);
						myHandler.sendMessage(msg);
					}
				} catch (Exception e) {
					Message msg=myHandler.obtainMessage(1);
					myHandler.sendMessage(msg);
					e.printStackTrace();
				}
			      } catch (Exception re) {
			      } finally {
			    	  multiFormatReader.reset();
			      }
	}

	private void launchSearch(String type,String typestr,String data,String dataStr) {
		    Intent intent = new Intent(Intents.Encode.ACTION);
		    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
		    intent.putExtra(type, typestr);
		    intent.putExtra(data, dataStr);
		    intent.putExtra(Intents.Encode.FORMAT, BarcodeFormat.QR_CODE.toString());
		    startActivity(intent);
		  }

	@Override
	protected void onPause() {
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
		timer.onPause();
		cameraManager.closeDriver();
		if (!hasSurface) {
			SurfaceView surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		timer.shutdown();
		super.onDestroy();
	}

	public CameraManager getCameraManager() {
	    return cameraManager;
	  }

	public void handleDecode(Result result, Bitmap barcode) {
		
		timer.onActivity();
		String resultString = result.getText();
		handleResult(resultString);
	}

	private void handleResult(String resultString) {
		
		if (resultString.equals("")) {
			Toast.makeText(CaptureActivity.this, "Scan failed!",
					Toast.LENGTH_SHORT).show();
		} else {
			ishandleDecode=true;
			Intent resultIntent = new Intent();
			Bundle bundle = new Bundle();
			bundle.putString("result", resultString);
			resultIntent.putExtras(bundle);
			this.setResult(RESULT_OK, resultIntent);
			if(resultString.startsWith("http"))
			{
				Intent intent = new Intent(Intent.ACTION_VIEW);
				Uri uri=Uri.parse(resultString);
				intent.setData(uri);
				startActivity(intent);
			}
			else
			{
				textView.setText(resultString);
				textView.setVisibility(View.VISIBLE);
				viewfinderView.setVisibility(View.GONE);
			}
			
		}
		if (handler != null) {
			handler.quitSynchronously();
			handler = null;
		}
	}

	private void initCamera(SurfaceHolder surfaceHolder) {
		if (surfaceHolder == null) {
			throw new IllegalStateException("No SurfaceHolder provided");
		}
		if (cameraManager.isOpen()) {
			Log.w("info",
					"initCamera() while already open -- late SurfaceView callback?");
			 return;
		}
		try {
			cameraManager.openDriver(surfaceHolder);
		} catch (Exception e) {
			return;
		}
		if (handler == null) {
			handler = new CaptureActivityHandler(this, decodeFormats, null,
					characterSet, cameraManager);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;

	}

	public ViewfinderView getViewfinderView() {
		return viewfinderView;
	}

	public Handler getHandler() {
		return handler;
	}

	public void drawViewfinder() {
		viewfinderView.drawViewfinder();

	}
	
	@Override
	public void onBackPressed() {
		if(ishandleDecode)
		{
			ishandleDecode=false;
			textView.setText("");
			textView.setVisibility(View.GONE);
			viewfinderView.setVisibility(View.VISIBLE);
			handler = new CaptureActivityHandler(this, decodeFormats, null,
					characterSet, cameraManager);
		}
		else
		{
			cameraManager.closeDriver();
			super.onBackPressed();
		}
	}

}
