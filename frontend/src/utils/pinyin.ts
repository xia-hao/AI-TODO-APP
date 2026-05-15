export interface MentionMember {
  userId: number
  username: string
  displayName: string
}

export function matchMembers(query: string, members: MentionMember[]): MentionMember[] {
  const lower = query.toLowerCase()
  return members.filter(m => {
    if (m.displayName.toLowerCase().includes(lower)) return true
    if (m.username.toLowerCase().includes(lower)) return true
    // TODO: pinyin matching for Chinese characters
    // if (toPinyin(m.displayName).includes(lower)) return true
    return false
  })
}
