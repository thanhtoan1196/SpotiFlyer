/*
 * Copyright (c)  2021  Shabinder Singh
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.shabinder.common.providers

import com.shabinder.common.*
import com.shabinder.common.gaana.GaanaRequests
import com.shabinder.common.gaana.GaanaTrack
import com.shabinder.common.spotify.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GaanaProvider: PlatformDir(),GaanaRequests {

    private val gaanaPlaceholderImageUrl = "https://a10.gaanacdn.com/images/social/gaana_social.jpg"

    suspend fun query(fullLink: String): PlatformQueryResult?{
        //Link Schema: https://gaana.com/type/link
        val gaanaLink = fullLink.substringAfter("gaana.com/")

        val link = gaanaLink.substringAfterLast('/', "error")
        val type = gaanaLink.substringBeforeLast('/', "error").substringAfterLast('/')

        //Error
        if (type == "Error" || link == "Error"){
            return null
        }
        return gaanaSearch(
            type,
            link
        )
    }

    private suspend fun gaanaSearch(
        type:String,
        link:String,
    ): PlatformQueryResult {
        val result = PlatformQueryResult(
            folderType = "",
            subFolder = link,
            title = link,
            coverUrl = gaanaPlaceholderImageUrl,
            trackList = listOf(),
            Source.Gaana
        )
        with(result) {
            when (type) {
                "song" -> {
                    getGaanaSong(seokey = link).tracks.firstOrNull()?.also {
                        folderType = "Tracks"
                        subFolder = ""
                        if (isPresent(
                                finalOutputDir(
                                    it.track_title,
                                    folderType,
                                    subFolder,
                                    defaultDir()
                                )
                            )) {//Download Already Present!!
                            it.downloaded = DownloadStatus.Downloaded
                        }
                        trackList = listOf(it).toTrackDetailsList(folderType, subFolder)
                        title = it.track_title
                        coverUrl = it.artworkLink
                        withContext(Dispatchers.Default) {
                            databaseDAO.insert(
                                DownloadRecord(
                                    type = "Track",
                                    name = title,
                                    link = "https://gaana.com/$type/$link",
                                    coverUrl = coverUrl,
                                    totalFiles = 1,
                                )
                            )
                        }
                    }
                }
                "album" -> {
                    getGaanaAlbum(seokey = link).also {
                        folderType = "Albums"
                        subFolder = link
                        it.tracks.forEach { track ->
                            if (isPresent(
                                    finalOutputDir(
                                        track.track_title,
                                        folderType,
                                        subFolder,
                                        defaultDir()
                                    )
                                )
                            ) {//Download Already Present!!
                                track.downloaded = DownloadStatus.Downloaded
                            }
                        }
                        trackList = it.tracks.toTrackDetailsList(folderType, subFolder)
                        title = link
                        coverUrl = it.custom_artworks.size_480p
                        withContext(Dispatchers.Default) {
                            databaseDAO.insert(
                                DownloadRecord(
                                    type = "Album",
                                    name = title,
                                    link = "https://gaana.com/$type/$link",
                                    coverUrl = coverUrl,
                                    totalFiles = trackList.size,
                                )
                            )
                        }
                    }
                }
                "playlist" -> {
                    getGaanaPlaylist(seokey = link).also {
                        folderType = "Playlists"
                        subFolder = link
                        it.tracks.forEach { track ->
                            if (isPresent(
                                    finalOutputDir(
                                        track.track_title,
                                        folderType,
                                        subFolder,
                                        defaultDir()
                                    )
                                )
                            ) {//Download Already Present!!
                                track.downloaded = DownloadStatus.Downloaded
                            }
                        }
                        trackList = it.tracks.toTrackDetailsList(folderType, subFolder)
                        title = link
                        //coverUrl.value = "TODO"
                        coverUrl = gaanaPlaceholderImageUrl
                        withContext(Dispatchers.Default) {
                            databaseDAO.insert(
                                DownloadRecord(
                                    type = "Playlist",
                                    name = title,
                                    link = "https://gaana.com/$type/$link",
                                    coverUrl = coverUrl,
                                    totalFiles = it.tracks.size,
                                )
                            )
                        }
                    }
                }
                "artist" -> {
                    folderType = "Artist"
                    subFolder = link
                    coverUrl = gaanaPlaceholderImageUrl
                    val artistDetails =
                        getGaanaArtistDetails(seokey = link).artist?.firstOrNull()
                            ?.also {
                                title = it.name
                                coverUrl = it.artworkLink ?: gaanaPlaceholderImageUrl
                            }
                    getGaanaArtistTracks(seokey = link).also {
                        it.tracks.forEach { track ->
                            if (isPresent(
                                    finalOutputDir(
                                        track.track_title,
                                        folderType,
                                        subFolder,
                                        defaultDir()
                                    )
                                )
                            ) {//Download Already Present!!
                                track.downloaded = DownloadStatus.Downloaded
                            }
                        }
                        trackList = it.tracks.toTrackDetailsList(folderType, subFolder)
                        withContext(Dispatchers.Default) {
                            databaseDAO.insert(
                                DownloadRecord(
                                    type = "Artist",
                                    name = artistDetails?.name ?: link,
                                    link = "https://gaana.com/$type/$link",
                                    coverUrl = coverUrl,
                                    totalFiles = trackList.size,
                                )
                            )
                        }
                    }
                }
                else -> {//TODO Handle Error}
                }
            }
            return result
        }
    }

    private fun List<GaanaTrack>.toTrackDetailsList(type:String, subFolder:String) = this.map {
        TrackDetails(
            title = it.track_title,
            artists = it.artist.map { artist -> artist?.name.toString() },
            durationSec = it.duration,
//            albumArt = File(
//                imageDir + (it.artworkLink.substringBeforeLast('/').substringAfterLast('/')) + ".jpeg"),
            albumName = it.album_title,
            year = it.release_date,
            comment = "Genres:${it.genre?.map { genre -> genre?.name }?.reduceOrNull { acc, s -> acc + s  }}",
            trackUrl = it.lyrics_url,
            downloaded = it.downloaded ?: DownloadStatus.NotDownloaded,
            source = Source.Gaana,
            albumArtURL = it.artworkLink,
            outputFile = finalOutputDir(it.track_title,type, subFolder,defaultDir(),".m4a")
        )
    }
}