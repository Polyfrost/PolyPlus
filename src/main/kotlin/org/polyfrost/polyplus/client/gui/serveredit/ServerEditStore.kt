package org.polyfrost.polyplus.client.gui.serveredit

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import org.polyfrost.polyplus.client.featured.FeaturedServers

internal object ServerEditStore {
    fun save(server: ServerData?, index: Int, name: String, address: String, packStatus: ServerData.ServerPackStatus) {
        if (server == null) add(name, address, packStatus) else update(server, index, name, address, packStatus)
    }

    fun delete(server: ServerData, index: Int) {
        FeaturedServers.dismissMultiplayerByAddress(server.ip)
        val list = loadList()
        list.find(server, index)?.let(list::remove)
        list.save()
    }

    private fun add(name: String, address: String, packStatus: ServerData.ServerPackStatus) {
        val draft = ServerData(name, address, ServerData.Type.OTHER)
        draft.setResourcePackStatus(packStatus)
        val list = loadList()
        val hidden = list.unhide(address)
        if (hidden != null) {
            hidden.copyNameIconFrom(draft)
            hidden.setResourcePackStatus(packStatus)
        } else {
            list.add(draft, false)
        }
        list.save()
    }

    private fun update(server: ServerData, index: Int, name: String, address: String, packStatus: ServerData.ServerPackStatus) {
        val list = loadList()
        val entry = list.find(server, index)
        if (entry == null) list.add(server, false)
        listOfNotNull(entry, server).forEach {
            it.name = name
            it.ip = address
            it.setState(ServerData.State.INITIAL)
            it.setResourcePackStatus(packStatus)
        }
        list.save()
    }

    private fun loadList() = ServerList(Minecraft.getInstance()).apply { load() }

    private fun ServerList.find(server: ServerData, index: Int): ServerData? {
        if (index in 0 until size()) get(index).takeIf { it.matches(server) }?.let { return it }
        return (0 until size()).map(::get).firstOrNull { it.matches(server) } ?: get(server.ip)
    }

    private fun ServerData.matches(other: ServerData) = ip == other.ip && name == other.name
}
