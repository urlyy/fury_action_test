// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

use crate::{buffer::Writer, Error};
use byteorder::{ByteOrder, LittleEndian};
use chrono::{DateTime, Days, NaiveDate, NaiveDateTime};
use std::collections::BTreeMap;
use std::marker::PhantomData;

use super::{
    reader::{ArrayViewer, MapViewer},
    writer::{ArrayWriter, MapWriter},
};

pub trait Row<'a> {
    type ReadResult;

    fn write(v: &Self, writer: &mut Writer);

    fn cast(bytes: &'a [u8]) -> Self::ReadResult;
}

fn read_i8_from_bytes(bytes: &[u8]) -> i8 {
    bytes[0] as i8
}

macro_rules! impl_row_for_number {
    ($tt: tt, $writer: expr ,$visitor: expr) => {
        impl<'a> Row<'a> for $tt {
            type ReadResult = Self;

            fn write(v: &Self, writer: &mut Writer) {
                $writer(writer, *v);
            }

            fn cast(bytes: &[u8]) -> Self::ReadResult {
                $visitor(bytes)
            }
        }
    };
}
impl_row_for_number!(i8, Writer::i8, read_i8_from_bytes);
impl_row_for_number!(i16, Writer::i16, LittleEndian::read_i16);
impl_row_for_number!(i32, Writer::i32, LittleEndian::read_i32);
impl_row_for_number!(i64, Writer::i64, LittleEndian::read_i64);
impl_row_for_number!(f32, Writer::f32, LittleEndian::read_f32);
impl_row_for_number!(f64, Writer::f64, LittleEndian::read_f64);

impl<'a> Row<'a> for String {
    type ReadResult = &'a str;

    fn write(v: &Self, writer: &mut Writer) {
        writer.bytes(v.as_bytes());
    }

    fn cast(bytes: &'a [u8]) -> Self::ReadResult {
        unsafe { std::str::from_utf8_unchecked(bytes) }
    }
}

impl<'a> Row<'a> for bool {
    type ReadResult = Self;

    fn write(v: &Self, writer: &mut Writer) {
        writer.u8(if *v { 1 } else { 0 });
    }

    fn cast(bytes: &[u8]) -> Self::ReadResult {
        bytes[0] == 1
    }
}

lazy_static::lazy_static!(
    static ref EPOCH: NaiveDate = NaiveDate::from_ymd_opt(1970, 1, 1).unwrap();
);

impl<'a> Row<'a> for NaiveDate {
    type ReadResult = Result<NaiveDate, Error>;

    fn write(v: &Self, writer: &mut Writer) {
        let days_since_epoch = v.signed_duration_since(*EPOCH).num_days();
        writer.u32(days_since_epoch as u32);
    }

    fn cast(bytes: &[u8]) -> Self::ReadResult {
        let days = LittleEndian::read_u32(bytes);
        match EPOCH.checked_add_days(Days::new(days.into())) {
            Some(value) => Ok(value),
            None => Err(Error::NaiveDate),
        }
    }
}

impl<'a> Row<'a> for NaiveDateTime {
    type ReadResult = Result<NaiveDateTime, Error>;

    fn write(v: &Self, writer: &mut Writer) {
        writer.i64(v.and_utc().timestamp_millis());
    }

    fn cast(bytes: &[u8]) -> Self::ReadResult {
        let timestamp = LittleEndian::read_u64(bytes);
        let ret = DateTime::from_timestamp_millis(timestamp as i64).map(|dt| dt.naive_utc());
        match ret {
            Some(r) => Ok(r),
            None => Err(Error::NaiveDateTime),
        }
    }
}

impl<'a> Row<'a> for Vec<u8> {
    type ReadResult = &'a [u8];

    fn write(v: &Self, writer: &mut Writer) {
        writer.bytes(v);
    }

    fn cast(bytes: &'a [u8]) -> Self::ReadResult {
        bytes
    }
}

pub struct ArrayGetter<'a, T> {
    array_data: ArrayViewer<'a>,
    _marker: PhantomData<T>,
}

impl<'a, T: Row<'a>> ArrayGetter<'a, T> {
    pub fn size(&self) -> usize {
        self.array_data.num_elements()
    }

    pub fn get(&self, idx: usize) -> T::ReadResult {
        if idx >= self.array_data.num_elements() {
            panic!("out of bound");
        }
        let bytes = self.array_data.get_field_bytes(idx);
        <T as Row>::cast(bytes)
    }
}

impl<'a, T: Row<'a>> Row<'a> for Vec<T> {
    type ReadResult = ArrayGetter<'a, T>;

    fn write(v: &Self, writer: &mut Writer) {
        let mut array_writer = ArrayWriter::new(v.len(), writer);
        v.iter().enumerate().for_each(|(idx, item)| {
            let callback_info = array_writer.write_start(idx);
            <T as Row>::write(item, array_writer.get_writer());
            array_writer.write_end(callback_info);
        });
    }

    fn cast(row: &'a [u8]) -> Self::ReadResult {
        ArrayGetter {
            array_data: ArrayViewer::new(row),
            _marker: PhantomData::<T>,
        }
    }
}

pub struct MapGetter<'a, T1, T2>
where
    T1: Ord,
    T2: Ord,
{
    map_data: MapViewer<'a>,
    _key_marker: PhantomData<T1>,
    _value_marker: PhantomData<T2>,
}

impl<'a, T1: Row<'a> + Ord, T2: Row<'a> + Ord> MapGetter<'a, T1, T2> {
    pub fn to_btree_map(&'a self) -> Result<BTreeMap<T1::ReadResult, T2::ReadResult>, Error>
    where
        <T1 as Row<'a>>::ReadResult: Ord,
    {
        let mut map = BTreeMap::new();
        let keys = self.keys();
        let values = self.values();

        for i in 0..self.keys().size() {
            map.insert(keys.get(i), values.get(i));
        }
        Ok(map)
    }

    pub fn keys(&'a self) -> ArrayGetter<'a, T1> {
        ArrayGetter {
            array_data: ArrayViewer::new(self.map_data.get_key_row()),
            _marker: PhantomData::<T1>,
        }
    }

    pub fn values(&'a self) -> ArrayGetter<'a, T2> {
        ArrayGetter {
            array_data: ArrayViewer::new(self.map_data.get_value_row()),
            _marker: PhantomData::<T2>,
        }
    }
}

impl<'a, T1: Row<'a> + Ord, T2: Row<'a> + Ord> Row<'a> for BTreeMap<T1, T2> {
    type ReadResult = MapGetter<'a, T1, T2>;

    fn write(v: &Self, writer: &mut Writer) {
        let mut map_writter = MapWriter::new(writer);
        {
            let callback_info = map_writter.write_start(0);
            let mut array_writer = ArrayWriter::new(v.len(), map_writter.get_writer());
            v.keys().enumerate().for_each(|(idx, item)| {
                let callback_info = array_writer.write_start(idx);
                <T1 as Row>::write(item, array_writer.get_writer());
                array_writer.write_end(callback_info);
            });
            map_writter.write_end(callback_info);
        }
        {
            let mut array_writer = ArrayWriter::new(v.len(), map_writter.get_writer());
            v.values().enumerate().for_each(|(idx, item)| {
                let callback_info = array_writer.write_start(idx);
                <T2 as Row>::write(item, array_writer.get_writer());
                array_writer.write_end(callback_info);
            });
        }
    }

    fn cast(row: &'a [u8]) -> Self::ReadResult {
        MapGetter {
            map_data: MapViewer::new(row),
            _key_marker: PhantomData::<T1>,
            _value_marker: PhantomData::<T2>,
        }
    }
}
