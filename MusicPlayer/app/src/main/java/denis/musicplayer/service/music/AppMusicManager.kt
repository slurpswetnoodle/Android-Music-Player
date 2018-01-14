package denis.musicplayer.service.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.util.Log
import android.widget.RemoteViews
import denis.musicplayer.R
import denis.musicplayer.data.DataManager
import denis.musicplayer.data.media.model.Track
import denis.musicplayer.di.ApplicationContext
import denis.musicplayer.service.AppMusicService
import denis.musicplayer.ui.main.MainActivity
import denis.musicplayer.utils.BytesUtil
import io.reactivex.subjects.BehaviorSubject
import javax.inject.Singleton
import javax.inject.Inject


/**
 * Created by denis on 10/01/2018.
 */

@Singleton
class AppMusicManager
    @Inject constructor(@ApplicationContext val context: Context,
                        val dataManager: DataManager) : MusicManager, MediaPlayer.OnCompletionListener {

    private val TAG = "AppMusicManager"

    companion object {
        val KEY_ACTION = "key_action"
    }

    private var channelId = "music_channel_id"

    private val mediaPlayer: MediaPlayer = MediaPlayer()
    private var tracks = ArrayList<Track>()
    private var currentTrackPosition = 0
    private var resumePosition = 0
    private var action: Unit? = null

    private val currentTrackBehaviour: BehaviorSubject<Track> = BehaviorSubject.create()
    private val actionBehaviour: BehaviorSubject<MusicManagerAction> = BehaviorSubject.create()

    private var musicService: AppMusicService? = null
    override fun setService(service: AppMusicService) {
        this.musicService = service
    }

    init {
        initMediaPlayer()
    }

    private fun initMediaPlayer() {
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setAudioAttributes(audioAttributes)
    }

    override fun getCurrentTrackBehaviour(): BehaviorSubject<Track> = currentTrackBehaviour

    override fun actionBehaviour(): BehaviorSubject<MusicManagerAction> = actionBehaviour

    override fun playTrack() {
        Log.d(TAG, "playTrack")
        mediaPlayer.stop()
        mediaPlayer.reset()
        mediaPlayer.setDataSource(tracks[currentTrackPosition].data)
        mediaPlayer.prepare()
        mediaPlayer.start()
        currentTrackBehaviour.onNext(tracks[currentTrackPosition])
        actionBehaviour.onNext(MusicManagerAction.PLAY)
        buildNotification()
    }

    override fun pauseTrack() {
        Log.d(TAG, "pauseTrack")
        resumePosition = mediaPlayer.currentPosition
        mediaPlayer.pause()
        actionBehaviour.onNext(MusicManagerAction.PAUSE)
        buildNotification()
    }

    override fun resumeTrack() {
        Log.d(TAG, "resumeTrack")
        mediaPlayer.seekTo(resumePosition)
        mediaPlayer.start()
        actionBehaviour.onNext(MusicManagerAction.PLAY)
        buildNotification()
    }

    override fun updateTracks(tracks: ArrayList<Track>, currentTrackPosition: Int) {
        this.tracks = tracks
        this.currentTrackPosition = currentTrackPosition
    }

    override fun previousTrack() {
        Log.d(TAG, "previousTrack")
        when(currentTrackPosition - 1) {
            -1 -> currentTrackPosition = tracks.size - 1
            else -> currentTrackPosition--
        }
        playTrack()
    }

    override fun nextTrack() {
        Log.d(TAG, "nextTrack")
        when(currentTrackPosition + 1) {
            tracks.size -> currentTrackPosition = 0
            else -> currentTrackPosition++
        }
        playTrack()
    }

    override fun buildNotification() {
        val view = RemoteViews(context.packageName, R.layout.notification_music_player)
        val currentTrack = getCurrentTrack()

        view.setTextViewText(R.id.trackTitle, currentTrack.title)
        view.setTextViewText(R.id.trackArtist, currentTrack.artist)

        val bitmap = BitmapFactory.decodeFile(dataManager.getAlbumImagePath(currentTrack.albumId))

        when(bitmap) {
            null -> view.setImageViewResource(R.id.cover, R.drawable.no_music)
            else -> view.setImageViewBitmap(R.id.cover, bitmap)
        }


        view.setOnClickPendingIntent(R.id.next, handleActions(MusicManagerAction.NEXT))
        view.setOnClickPendingIntent(R.id.previous, handleActions(MusicManagerAction.PREVIOUS))
        view.setOnClickPendingIntent(R.id.close, handleActions(MusicManagerAction.CLOSE))

        when(isPlaying()) {
            true -> {
                view.setImageViewResource(R.id.playPause, R.drawable.pause)
                view.setOnClickPendingIntent(R.id.playPause, handleActions(MusicManagerAction.PAUSE))
            }
            false -> {
                view.setImageViewResource(R.id.playPause, R.drawable.play)
                view.setOnClickPendingIntent(R.id.playPause, handleActions(MusicManagerAction.RESUME))
            }
        }

        val intent = Intent(context, MainActivity::class.java)
        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val pIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        channelId = if(Build.VERSION.SDK_INT >=  Build.VERSION_CODES.O) createNotificationChannel() else ""

        val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.music)
                .setContent(view)
                .setContentIntent(pIntent)
                .build()

        musicService?.showNotificationForeground(notification)
    }

    override fun closeMusicPlayer() {
        actionBehaviour.onNext(MusicManagerAction.PAUSE)
        mediaPlayer.stop()
        musicService?.stopService()
    }

    override fun isPlaying(): Boolean = mediaPlayer.isPlaying

    private fun handleActions(action: MusicManagerAction): PendingIntent? {
        Log.d(TAG, "PUT ACTION : $action")
        val intent = Intent(context, AppMusicService::class.java)
        val bundle = BytesUtil.toByteArray(action)
        intent.putExtra(KEY_ACTION, bundle)
        return PendingIntent.getService(context, action.value, intent, 0)
    }

    override fun getCurrentTrack(): Track = tracks[currentTrackPosition]

    override fun onCompletion(p0: MediaPlayer?) {
        nextTrack()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = "android_music_service"
        val channelName = "Android Music Service"
        val chan = NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.BLUE
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    override fun callAction() {
        if(tracks.size > 0) {
            when (isPlaying()) {
                true -> pauseTrack()
                false -> resumeTrack()
            }
        }
    }

    override fun makeAction(action: MusicManagerAction) {
        Log.d(TAG, "makeAction : $action")
        when(action) {
            MusicManagerAction.PLAY -> playTrack()
            MusicManagerAction.PAUSE -> pauseTrack()
            MusicManagerAction.PREVIOUS -> previousTrack()
            MusicManagerAction.NEXT -> nextTrack()
            MusicManagerAction.RESUME -> resumeTrack()
            else -> closeMusicPlayer()
        }
    }
}

enum class MusicManagerAction(val value: Int) {
    PLAY(0),
    PAUSE(1),
    RESUME(2),
    PREVIOUS(3),
    NEXT(4),
    CLOSE(5)
}