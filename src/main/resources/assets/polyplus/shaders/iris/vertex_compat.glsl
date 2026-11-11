vec4 polyplus_vertex() {
    vec4 polyplus_position = gl_Vertex;
    polyplus_position.y += polyplus_lift(polyplus_position.xyz);
    return polyplus_position;
}
