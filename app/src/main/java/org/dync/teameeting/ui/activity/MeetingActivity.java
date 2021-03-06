package org.dync.teameeting.ui.activity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.anyrtc.AnyrtcM2Mutlier;
import org.anyrtc.m2multier.M2MPublisher;
import org.anyrtc.m2multier.M2MultierEvents;
import org.dync.teameeting.R;
import org.dync.teameeting.sdkmsgclientandroid.jni.JMClientType;
import org.dync.teameeting.structs.EventType;
import org.dync.teameeting.ui.adapter.ChatMessageAdapter;
import org.dync.teameeting.http.NetWork;
import org.dync.teameeting.TeamMeetingApp;
import org.dync.teameeting.ui.helper.Anims;
import org.dync.teameeting.ui.helper.DialogHelper;
import org.dync.teameeting.ui.helper.MeetingAnim;
import org.dync.teameeting.ui.helper.MeetingAnim.AnimationEndListener;
import org.dync.teameeting.widgets.PopupWindowCustom;
import org.dync.teameeting.widgets.PopupWindowCustom.OnPopupWindowClickListener;
import org.dync.teameeting.widgets.RoomControls;
import org.dync.teameeting.utils.ChatMessage;
import org.dync.teameeting.utils.ChatMessage.Type;
import org.webrtc.StatsReport;

import android.animation.Animator;
import android.content.Intent;
import android.content.res.Configuration;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nineoldandroids.view.ViewPropertyAnimator;
import com.ypy.eventbus.EventBus;

/**
 * @author zhangqilu org.dync.teammeeting.activity MeetingActivity create at
 *         2015-12-11 5:02:32
 */

public class MeetingActivity extends MeetingBaseActivity implements M2MultierEvents
{
    // Local preview screen position before call is connected.
    private static final boolean mDebug = TeamMeetingApp.mIsDebug;
    private static final String TAG = "MeetingActivity";

    private static final int ANIMATOR_TANSLATION = 0X01;

    private AnyrtcM2Mutlier mAnyM2Mutlier;
    private MeetingAnim mMettingAnim;
    private ImageButton mChatButton, mInviteButton;
    private RoomControls mControlLayout;
    private RelativeLayout mTopbarLayout;
    private ImageButton mVoiceButton, mCameraButton, mHangUpButton,
            mSwitchCameraButton, mCameraOffButton;
    private boolean mMeetingCameraFlag = true, mMeetingCameraOffFlag = true,
            mMeetingVideoFlag = true, mMeetingVoiceFlag;

    private TextView mTvRoomName;

    private String mBsNow, mBsBefore;

    double mTsNow, mTsBefore;
    private String mBitrate;

    private PopupWindowCustom mPopupWindowCustom;

    // Left distance of this control button relative to its parent
    int mLeftDistanceCameraBtn;
    int mLeftDistanceHangUpBtn;
    int mLeftDistanceVoiceBtn;

    // chating
    private RelativeLayout mChatLayout;
    private ImageButton mChatClose;
    private Button mSendMessage;
    private TextView mTvRemind;
    private String mUserId;
    private final String mPass = TeamMeetingApp.getmSelfData().getAuthorization();
    private boolean mMessageShowFlag=true;


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    private ListView mChatView;
    private ImageView mCloseVoice;
    private EditText mMsg;
    private List<ChatMessage> mDatas = new ArrayList<ChatMessage>();
    private ChatMessageAdapter mAdapter;
    private InputMethodManager mIMM;
    boolean mChatLayoutShow = false;
    private String mMeetingId;
    private NetWork mNetWork;

    private Handler mUiHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
                case ANIMATOR_TANSLATION:

                    mVoiceButton.setVisibility(View.VISIBLE);
                    mHangUpButton.setVisibility(View.VISIBLE);
                    mSwitchCameraButton.setVisibility(View.GONE);
                    mCameraOffButton.setVisibility(View.GONE);

                    break;

                default:
                    break;
            }

        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mMeetingId = intent.getStringExtra("meetingId");
        mUserId = intent.getStringExtra("userId");
        EventBus.getDefault().register(this);
        if (mDebug)
        {
            Log.i(TAG, "meetingId" + mMeetingId);
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mAnyM2Mutlier = new AnyrtcM2Mutlier(this, this);
        mAnyM2Mutlier.InitVideoView((GLSurfaceView) findViewById(R.id.glview_call));
        initView();

        {
            M2MPublisher.PublishParams params = new M2MPublisher.PublishParams();
            params.bEnableVideo = true;
            params.eStreamType = M2MPublisher.StreamType.ST_RTC;
            mAnyM2Mutlier.Publish(params);
        }
    }

    /* Init UI */
    private void initView()
    {
        mNetWork = new NetWork();
        mMettingAnim = new MeetingAnim();
        mMettingAnim.setAnimEndListener(mAnimationEndListener);

        mIMM = (InputMethodManager) MeetingActivity.this
                .getSystemService(MainActivity.INPUT_METHOD_SERVICE);

        // Create UI controls.
        mTopbarLayout = (RelativeLayout) findViewById(R.id.rl_meeting_topbar);
        mControlLayout = (RoomControls) findViewById(R.id.rl_meeting_control);

        mChatButton = (ImageButton) findViewById(R.id.imgbtn_chat);
        mInviteButton = (ImageButton) findViewById(R.id.imgbtn_invite);
        mTvRoomName = (TextView) findViewById(R.id.tv_room_name);
        mTvRemind = (TextView) findViewById(R.id.tv_remind);
        String roomName = getIntent().getStringExtra("meetingName");
        mTvRoomName.setText(roomName);

        mCloseVoice = (ImageView) findViewById(R.id.iv_close_voice);
        mVoiceButton = (ImageButton) findViewById(R.id.meeting_voice);
        mCameraButton = (ImageButton) findViewById(R.id.meeting_camera);
        mHangUpButton = (ImageButton) findViewById(R.id.meeting_hangup);
        mSwitchCameraButton = (ImageButton) findViewById(R.id.meeting_camera_switch);
        mCameraOffButton = (ImageButton) findViewById(R.id.meeting_camera_off);

        mInviteButton.setOnClickListener(onClickListener);
        mChatButton.setOnClickListener(onClickListener);
        mVoiceButton.setOnClickListener(onClickListener);
        mCameraButton.setOnClickListener(onClickListener);
        mHangUpButton.setOnClickListener(onClickListener);
        mSwitchCameraButton.setOnClickListener(onClickListener);
        mCameraOffButton.setOnClickListener(onClickListener);

        // Chat ui inint
        mChatLayout = (RelativeLayout) findViewById(R.id.rl_chating);
        mSendMessage = (Button) findViewById(R.id.btn_chat_send);
        mChatClose = (ImageButton) findViewById(R.id.imgbtn_back);
        mChatView = (ListView) findViewById(R.id.listView_chat);
        mMsg = (EditText) findViewById(R.id.et_chat_msg);
        mSendMessage.setOnClickListener(onClickListener);
        mChatClose.setOnClickListener(onClickListener);

        mAdapter = new ChatMessageAdapter(this, mDatas);
        mChatView.setAdapter(mAdapter);

    }


    float downX = 0;
    float downY = 0;

    @Override
    public boolean onTouchEvent(MotionEvent event)
    {

        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                float moveX = event.getX() - downX;
                float moveY = event.getY() - downY;
                if (Math.abs(moveX) > Math.abs(moveY) && TeamMeetingApp.isPad)
                {
                    chatLayoutControl(moveX);
                } else
                {
                    contralAnim();
                }

                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * chataShow
     *
     * @param moveX
     */
    public void chatLayoutControl(float moveX)
    {
        int controllMove = controllerMoveDistance(mChatLayout);
        int showTime = 500;
        if (moveX > 0 && !mChatLayoutShow)
        {
            mChatLayoutShow = true;
            Anims.animateRightMarginTo(mChatLayout, 0, mChatLayout.getWidth() - 10, showTime, Anims.ACCELERATE);
            Anims.animateRightMarginTo(mControlLayout, 0, controllMove, showTime, Anims.ACCELERATE);
            Anims.animateRightMarginTo(mTvRemind, 0, controllMove, showTime, Anims.ACCELERATE);
            Anims.animateRightMarginTo(mTvRoomName, 0, controllMove, showTime, Anims.ACCELERATE);

        }
        if (moveX < 0 && mChatLayoutShow)
        {
            mChatLayoutShow = false;
            Anims.animateRightMarginTo(mChatLayout, mChatLayout.getWidth() - 10, 0, showTime, Anims.ACCELERATE);
            Anims.animateRightMarginTo(mControlLayout, controllMove, 0, showTime, Anims.ACCELERATE);
            Anims.animateRightMarginTo(mTvRemind, controllMove, 0, showTime, Anims.ACCELERATE);
            Anims.animateRightMarginTo(mTvRoomName, controllMove, 0, showTime, Anims.ACCELERATE);
        }
    }

    private void contralAnim()
    {
        if (mControlLayout.mAvailable)
        {
            mControlLayout.hide();

            ViewPropertyAnimator.animate(mTopbarLayout).translationY(
                    -mTopbarLayout.getHeight());
            ViewPropertyAnimator.animate(mCloseVoice).translationY(
                    -mTopbarLayout.getHeight());
        } else
        {
            mControlLayout.show();
            ViewPropertyAnimator.animate(mTopbarLayout).translationY(0f);
            ViewPropertyAnimator.animate(mCloseVoice).translationY(0f);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {

        if (mPopupWindowCustom != null)
        {
            mPopupWindowCustom.dismiss();
            mPopupWindowCustom = null;
        }

        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {

        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_DOWN
                && mChatLayout.getVisibility() == View.VISIBLE)
        {
            mChatLayout.setVisibility(View.GONE);
            mTopbarLayout.setVisibility(View.VISIBLE);
            mControlLayout.setVisibility(View.VISIBLE);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus)
    {
        measureLeftDistance();
        super.onWindowFocusChanged(hasFocus);
    }

    /**
     * Measuring the distance button
     */
    private void measureLeftDistance()
    {
        mLeftDistanceCameraBtn = mCameraButton.getLeft()
                + mCameraButton.getWidth() / 2;
        mLeftDistanceHangUpBtn = mHangUpButton.getLeft()
                + mHangUpButton.getWidth() / 2;
        mLeftDistanceVoiceBtn = mVoiceButton.getLeft()
                + mVoiceButton.getWidth() / 2;
    }

    private AnimationEndListener mAnimationEndListener = new AnimationEndListener()
    {
        @Override
        public void onAnimationEnd(Animator arg0)
        {
            mVoiceButton.setVisibility(View.VISIBLE);
            mHangUpButton.setVisibility(View.VISIBLE);
            mSwitchCameraButton.setVisibility(View.GONE);
            mCameraOffButton.setVisibility(View.GONE);
            mMettingAnim.alphaAnimator(mVoiceButton, 1.0f, 1.0f, 100);
            mMettingAnim.alphaAnimator(mHangUpButton, 1.0f, 1.0f, 100);
        }
    };

    private OnPopupWindowClickListener mPopupWindowListener = new OnPopupWindowClickListener()
    {
        @Override
        public void onPopupClickListener(View view)
        {
            mPopupWindowCustom.dismiss();
            switch (view.getId())
            {
                case R.id.ibtn_close:
                    // mPopupWindowCustom.dismiss();
                    break;
                case R.id.ibtn_message:
                    // mPopupWindowCustom.dismiss();
                    break;
                case R.id.ibtn_weixin:
                    // mPopupWindowCustom.dismiss();
                    break;
                case R.id.tv_copy:
                    // mPopupWindowCustom.dismiss();
                    break;
                case R.id.btn_copy:
                    // mPopupWindowCust kom.dismiss();
                    DialogHelper.onClickCopy(MeetingActivity.this,
                            "RoomUrl:www.baidu.com");
                    break;

                default:
                    break;
            }
        }

    };

    /* set button clickListener */
    OnClickListener onClickListener = new OnClickListener()
    {
        @Override
        public void onClick(View mView)
        {

            switch (mView.getId())
            {
                case R.id.imgbtn_invite:

                    mPopupWindowCustom = new PopupWindowCustom(
                            MeetingActivity.this, mInviteButton, mTopbarLayout,
                            mPopupWindowListener);
                    break;

                case R.id.meeting_camera:

                    if (!mMeetingCameraOffFlag)
                    {
                        mAnyM2Mutlier.SetLocalVideoEnabled(true);
                        mCameraButton.setImageResource(R.drawable.btn_camera_on);
                        mMeetingCameraOffFlag = true;
                        return;
                    }

                    if (mMeetingCameraFlag)
                    {
                        mCameraButton.setImageResource(R.drawable.btn_camera_back);
                        mVoiceButton.setVisibility(View.GONE);
                        mHangUpButton.setVisibility(View.GONE);
                        mSwitchCameraButton.setVisibility(View.VISIBLE);
                        mCameraOffButton.setVisibility(View.VISIBLE);

                        mMettingAnim.rotationOrApaha(mCameraButton,
                                mMeetingCameraFlag);
                        mMettingAnim.translationAlphaAnimator(mSwitchCameraButton,
                                (mLeftDistanceCameraBtn - mLeftDistanceHangUpBtn),
                                0, 400, true);

                        mMettingAnim.translationAlphaAnimator(mCameraOffButton,
                                (mLeftDistanceCameraBtn - mLeftDistanceVoiceBtn),
                                0, 400, true);

                    } else
                    {
                        mCameraButton.setImageResource(R.drawable.btn_camera_on);
                        mMettingAnim.rotationOrApaha(mCameraButton,
                                mMeetingCameraFlag);
                        mMettingAnim.translationAlphaAnimator(mSwitchCameraButton,
                                0,
                                (mLeftDistanceCameraBtn - mLeftDistanceHangUpBtn),
                                300, false);
                        mMettingAnim.translationAlphaAnimator(mCameraOffButton, 0,
                                (mLeftDistanceCameraBtn - mLeftDistanceVoiceBtn),
                                300, false);

                    }

                    mMeetingCameraFlag = !mMeetingCameraFlag;
                    break;
                case R.id.meeting_hangup:
                    finish();

                    int code = StartFlashActivity.mMsgSender.TMOptRoom(JMClientType.TMCMD_LEAVE,mUserId,mPass,mMeetingId,"");
                    if(code==0){
                        if(mDebug){
                            Log.e(TAG, "TMLeaveRoom Successed");
                        }
                    }else if(mDebug){
                        Log.e(TAG, "TMLeaveRoom Failed");
                    }

                    break;
                case R.id.meeting_voice:

                    if (mMeetingVoiceFlag)
                    {
                        mVoiceButton.setImageResource(R.drawable.btn_voice_off);
                        mCloseVoice.setVisibility(View.VISIBLE);
                        mAnyM2Mutlier.SetLocalAudioEnabled(false);
                    } else
                    {
                        mVoiceButton.setImageResource(R.drawable.btn_voice_on);
                        mCloseVoice.setVisibility(View.INVISIBLE);
                        mAnyM2Mutlier.SetLocalAudioEnabled(true);
                    }
                    mMeetingVoiceFlag = !mMeetingVoiceFlag;

                    break;
                case R.id.meeting_camera_switch:

                    mAnyM2Mutlier.SwitchCamera();

                    break;
                case R.id.meeting_camera_off:
                    mAnyM2Mutlier.SetLocalVideoEnabled(false);
                    mCameraButton
                            .setImageResource(R.drawable.btn_camera_off_select);
                    mMettingAnim.rotationOrApaha(mCameraButton, mMeetingCameraFlag);
                    mMettingAnim.translationAlphaAnimator(mSwitchCameraButton, 0,
                            (mLeftDistanceCameraBtn - mLeftDistanceHangUpBtn), 300,
                            false);
                    mMettingAnim.translationAlphaAnimator(mCameraOffButton, 0,
                            (mLeftDistanceCameraBtn - mLeftDistanceVoiceBtn), 300,
                            false);
                    mMeetingCameraOffFlag = false;
                    mMeetingCameraFlag = true;
                    break;
                case R.id.imgbtn_chat:

                    stopShowMessage();
                    if (TeamMeetingApp.isPad)
                    {
                        chatLayoutControl(100);
                    } else
                    {
                        mMessageShowFlag = false;
                        mChatLayout.setVisibility(View.VISIBLE);
                    }
                    break;
                case R.id.btn_chat_send:
                    sendMessageChat();
                    break;
                case R.id.imgbtn_back:
                    //startShowMessage();
                    mMessageShowFlag = true;
                    mChatLayout.setVisibility(View.GONE);
                    mIMM.hideSoftInputFromWindow(mMsg.getWindowToken(), 0);
                    break;
            }
        }
    };

    private void sendMessageChat()
    {
        final String pushMsg = mMsg.getText().toString();
        if (TextUtils.isEmpty(pushMsg))
        {
            Toast.makeText(this, R.string.str_content_empty, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // 推送测试
        String sign = TeamMeetingApp.getMyself().getmAuthorization();
        ChatMessage to = new ChatMessage(Type.OUTPUT, pushMsg,"name");
        to.setDate(new Date());
        mDatas.add(to);
        mAdapter.notifyDataSetChanged();
        mChatView.setSelection(mDatas.size() - 1);
        mMsg.setText("");

        mNetWork.pushMeetingMsg(sign, mMeetingId, "推送消息", "推送概要");

        int code = StartFlashActivity.mMsgSender.TMSndMsg(mUserId,mPass,mMeetingId,pushMsg);
        if(code==0){
            if(mDebug){
                Log.e(TAG, "sendMessageChat: "+"TMSndMsg Successed");
            }
        }else if(mDebug){
            Log.e(TAG, "sendMessageChat: "+"TMSndMsg Failed");
        }

    }

    /**
     * OnTouchListener
     */
    private View.OnTouchListener mOnTouchListener = new View.OnTouchListener()
    {
        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
            // TODO Auto-generated method stub
            if (v.getId() == R.id.meet_parent)
            {
                if (event.getAction() == MotionEvent.ACTION_UP)
                {
                    // View VISIBLE INVISIBLE
                    {
                        if (mDebug)
                        {
                            Log.e(TAG, " ");
                        }
                        if (mControlLayout.getVisibility() == View.VISIBLE)
                        {
                            mControlLayout.setVisibility(View.GONE);

                            return true;
                        } else if (mControlLayout.getVisibility() == View.GONE)
                        {
                            mControlLayout.setVisibility(View.VISIBLE);
                            return true;
                        } else
                        {
                            return false;
                        }
                    }
                }
                return true;
            } else
            {
                return false;
            }
        }
    };

    @Override
    public void onPause()
    {
        super.onPause();
        mAnyM2Mutlier.OnPause();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mAnyM2Mutlier.OnResume();
    }

    @Override
    protected void onDestroy()
    {
        // TODO Auto-generated method stub
        super.onDestroy();
        {// Close all
            if (mAnyM2Mutlier != null)
            {
                mAnyM2Mutlier.Destroy();
                mAnyM2Mutlier = null;
            }
        }
        EventBus.getDefault().unregister(this);
    }

    /**
     * get the Frame Rate, Actual Bitrate and Remote Candidate Type from report
     *
     * @param reports the video report
     */
    private void updateEncoderStatistics(StatsReport[] reports)
    {
        String fps = null;
        String targetBitrate = null;
        String actualBitrate = null;
        String recvByte = null;
        String sendByte = null;
        String bytesReceived;
        String reveivedHeight;
        Double reveivedTime;
        for (StatsReport report : reports)
        {

            bytesReceived = null;
            reveivedHeight = null;
            if (report.type.equals("ssrc"))
            {
                reveivedTime = report.timestamp;
                Map<String, String> reportMap = new HashMap<String, String>();

                for (StatsReport.Value value : report.values)
                {
                    reportMap.put(value.name, value.value);

                    if (value.name.equals("googFrameHeightReceived"))
                    {
                        reveivedHeight = value.value;
                        if (bytesReceived != null)
                        {
                            break;
                        }
                    }

                    if (value.name.equals("bytesReceived"))
                    {
                        bytesReceived = value.value;

                    }
                }

                if (bytesReceived != null && reveivedHeight != null)
                {
                    mBsNow = bytesReceived;
                    mTsNow = reveivedTime;

                    if (mBsBefore == null || mTsBefore == 0.0)
                    {
                        // Skip this round
                        mBsBefore = mBsNow;
                        mTsBefore = mTsNow;
                    } else
                    {
                        // Calculate bitrate

                        long tempBit = (Integer.parseInt(mBsNow) - Integer
                                .parseInt(mBsBefore));
                        if (tempBit < 0)
                            continue;
                        Double tempTime = (mTsNow - mTsBefore);
                        // Log.e(TAG,
                        // " tempBit "+tempBit+" tempTime "+tempTime);
                        long bitRate = Math.round(tempBit * 8 / tempTime);
                        mBitrate = bitRate + "KB/S";

                        mBsBefore = mBsNow;
                        mTsBefore = mTsNow;
                        mUiHandler.sendEmptyMessage(0x01);
                    }
                }

            }

        }

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
    }
    /**
     * For M2MultierEvents callback.
     * All callback is running run handle thread, so could update ui directly.
     */
    @Override
    public void OnRtcPublishOK(String publishId, String rtmpUrl, String hlsUrl) {
        //mAnyM2Mutlier.Subscribe(publishId, true);
        Toast.makeText(this, "PublishOK id: " + publishId, Toast.LENGTH_SHORT).show();
        StartFlashActivity.mMsgSender.TMNotifyMsg(mUserId,mPass,mMeetingId,publishId);
    }

    @Override
    public void OnRtcPublishFailed(int i, String s) {

    }

    @Override
    public void OnRtcPublishClosed() {

    }

    @Override
    public void OnRtcSubscribeOK(String s) {

    }

    @Override
    public void OnRtcSubscribeFailed(String s, int i, String s1) {

    }

    @Override
    public void OnRtcSubscribeClosed(String s) {

    }

    /**
     * For EventBus callback.
     */
    public void onEventMainThread(Message msg)
    {
        switch (EventType.values()[msg.what])
        {
            case MSG_MESSAGE_RECEIVE:
                int tags = msg.getData().getInt("tags");
                String message= msg.getData().getString("message");
                String name = msg.getData().getString("name");
                if(tags == 4) {
                    mAnyM2Mutlier.Subscribe(message, true);
                    return;
                }
                if (mDebug)
                    Log.e(TAG, "MSG_MESSAGE_RECEIVE  "+message);

                ChatMessage to = new ChatMessage(Type.INPUT, message,name);
                to.setDate(new Date());
                mDatas.add(to);
                mAdapter.notifyDataSetChanged();
                mChatView.setSelection(mDatas.size() - 1);
                mMsg.setText("");


                if(mMessageShowFlag){
                    addAutoView(message,name);
                }

                break;

            default:
                break;
        }
    }
}
