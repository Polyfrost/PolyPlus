vec3 polyplus_vaPosition() {
    vec3 polyplus_position = vaPosition + chunkOffset;
    polyplus_position.y += polyplus_lift(polyplus_position);
    return polyplus_position - chunkOffset;
}
