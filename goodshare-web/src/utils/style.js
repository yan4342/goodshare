export const getAvatarClass = (activeStyleLevel) => {
    const lvl = activeStyleLevel || 1
    if (lvl >= 10) return 'avatar-epic'
    if (lvl >= 5) return 'avatar-rare'
    if (lvl >= 3) return 'avatar-uncommon'
    return ''
}

export const getNameClass = (activeStyleLevel) => {
    const lvl = activeStyleLevel || 1
    if (lvl >= 10) return 'name-epic'
    if (lvl >= 5) return 'name-rare'
    if (lvl >= 3) return 'name-uncommon'
    return ''
}

export const getLevelTagType = (level) => {
    const lvl = level || 1
    if (lvl >= 10) return 'danger'
    if (lvl >= 5) return 'warning'
    if (lvl >= 3) return 'success'
    return 'info'
}