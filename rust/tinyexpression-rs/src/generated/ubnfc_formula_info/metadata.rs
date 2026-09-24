//! Grammar metadata; offsets in catalog sites refer to capture IDs, not input positions.
#[derive(Clone,Copy,Debug,PartialEq,Eq)] pub struct Catalog {pub rule_id:&'static str,pub rule:&'static str,pub context:&'static str,pub captures:&'static [&'static str],pub site_ids:&'static [&'static str]}
pub const CATALOGS:&[Catalog]=&[
];
