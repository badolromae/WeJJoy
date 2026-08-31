package com.jooshin.diary.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jooshin.diary.databinding.ItemMemberBinding
import com.jooshin.diary.sync.Member

/**
 * 공유자 목록.
 * 관리자에게만 '해제'(내보내기) / '다시 허용' 버튼이 보인다.
 */
class MemberAdapter(
    private val amOwner: Boolean,
    private val myUid: String,
    private val onRemove: (Member) -> Unit,
    private val onAllow: (Member) -> Unit
) : RecyclerView.Adapter<MemberAdapter.VH>() {

    private val items = ArrayList<Member>()

    fun submit(list: List<Member>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(val b: ItemMemberBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        val isMe = m.uid == myUid
        holder.b.memNick.text = m.nick + if (isMe) "  (나)" else ""
        holder.b.memRole.text = when {
            m.isOwner -> "관리자"
            m.isBanned -> "내보냄"
            else -> "공유자"
        }
        // 관리자만, 그리고 자기 자신은 못 내보낸다
        val canAct = amOwner && !isMe && !m.isOwner
        holder.b.memRemove.visibility = if (canAct) View.VISIBLE else View.GONE
        holder.b.memRemove.text = if (m.isBanned) "다시 허용" else "해제"
        holder.b.memRemove.setOnClickListener { if (m.isBanned) onAllow(m) else onRemove(m) }
    }
}
