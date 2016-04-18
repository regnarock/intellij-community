package org.jetbrains.builtInWebServer

import com.intellij.openapi.project.Project
import com.intellij.util.PathUtilRt
import io.netty.buffer.ByteBufUtf8Writer
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.*
import io.netty.handler.stream.ChunkedStream
import org.jetbrains.builtInWebServer.ssi.SsiExternalResolver
import org.jetbrains.builtInWebServer.ssi.SsiProcessor
import org.jetbrains.io.FileResponses
import org.jetbrains.io.addKeepAliveIfNeed
import org.jetbrains.io.okInSafeMode
import org.jetbrains.io.send
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private class StaticFileHandler : WebServerFileHandler() {
  override val pageFileExtensions = arrayOf("html", "htm", "shtml")

  private var ssiProcessor: SsiProcessor? = null

  override fun process(pathInfo: PathInfo, canonicalPath: CharSequence, project: Project, request: FullHttpRequest, channel: Channel, projectNameIfNotCustomHost: String?): Boolean {
    if (pathInfo.ioFile != null || pathInfo.file!!.isInLocalFileSystem) {
      val ioFile = pathInfo.ioFile ?: Paths.get(pathInfo.file!!.path)
      val nameSequence = ioFile.fileName.toString()
      //noinspection SpellCheckingInspection
      if (nameSequence.endsWith(".shtml", true) || nameSequence.endsWith(".stm", true) || nameSequence.endsWith(".shtm", true)) {
        processSsi(ioFile, PathUtilRt.getParentPath(canonicalPath.toString()), project, request, channel)
        return true
      }

      FileResponses.sendFile(request, channel, ioFile)
    }
    else {
      val file = pathInfo.file!!
      val response = FileResponses.prepareSend(request, channel, file.timeStamp, file.name) ?: return true

      val keepAlive = response.addKeepAliveIfNeed(request)
      if (request.method() != HttpMethod.HEAD) {
        HttpUtil.setContentLength(response, file.length)
      }

      channel.write(response)

      if (request.method() != HttpMethod.HEAD) {
        channel.write(ChunkedStream(file.inputStream))
      }

      val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
      if (!keepAlive) {
        future.addListener(ChannelFutureListener.CLOSE)
      }
    }

    return true
  }

  private fun processSsi(file: Path, path: String, project: Project, request: FullHttpRequest, channel: Channel) {
    if (ssiProcessor == null) {
      ssiProcessor = SsiProcessor(false)
    }

    val buffer = channel.alloc().ioBuffer()
    val keepAlive: Boolean
    var releaseBuffer = true
    try {
      val lastModified = ssiProcessor!!.process(SsiExternalResolver(project, request, path, file.parent), file, ByteBufUtf8Writer(buffer))
      val response = FileResponses.prepareSend(request, channel, lastModified, file.fileName.toString()) ?: return
      keepAlive = response.addKeepAliveIfNeed(request)
      if (request.method() != HttpMethod.HEAD) {
        HttpUtil.setContentLength(response, buffer.readableBytes().toLong())
      }

      channel.write(response)

      if (request.method() != HttpMethod.HEAD) {
        releaseBuffer = false
        channel.write(buffer)
      }
    }
    finally {
      if (releaseBuffer) {
        buffer.release()
      }
    }

    val future = channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    if (!keepAlive) {
      future.addListener(ChannelFutureListener.CLOSE)
    }
  }
}

internal fun checkAccess(channel: Channel, file: Path, request: HttpRequest, root: Path = file.root): Boolean {
  var parent = file
  do {
    if (!hasAccess(parent)) {
      HttpResponseStatus.FORBIDDEN.okInSafeMode().send(channel, request)
      return false
    }
    parent = parent.parent ?: break
  }
  while (parent != root)
  return true
}

// deny access to any dot prefixed file
private fun hasAccess(result: Path) = Files.isReadable(result) && !(Files.isHidden(result) || result.fileName.toString().startsWith('.'))