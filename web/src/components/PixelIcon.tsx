import type { SVGProps } from "react";

const assets = {
  bitcoin: {
    file: "bitcoin.svg",
    viewBox: "440 300 1420 1700",
    width: 2300,
    height: 2300,
  },
  wallet: {
    file: "wallet.svg",
    viewBox: "440 370 1420 1560",
    width: 2300,
    height: 2300,
  },
  message: {
    file: "message.svg",
    viewBox: "300 510 1701 1281",
    width: 2300,
    height: 2300,
  },
  key: {
    file: "key.svg",
    viewBox: "301 650 1698 1000",
    width: 2300,
    height: 2300,
  },
  lock: {
    file: "lock.svg",
    viewBox: "580 370 1140 1560",
    width: 2300,
    height: 2300,
  },
  share: {
    file: "share.svg",
    viewBox: "370 440 1560 1420",
    width: 2300,
    height: 2300,
  },
  lightning: {
    file: "lightning.svg",
    viewBox: "580 300 1140 1700",
    width: 2300,
    height: 2300,
  },
  "arrow-right": {
    file: "arrow-right.svg",
    viewBox: "108 316 2083 1667",
    width: 2300,
    height: 2300,
  },
  "arrow-up-right": {
    file: "arrow-up-right.svg",
    viewBox: "241 339 1721 1721",
    width: 2300,
    height: 2300,
  },
  "arrow-down-left": {
    file: "arrow-down-left.svg",
    viewBox: "339 241 1721 1721",
    width: 2300,
    height: 2300,
  },
  "arrow-left": {
    file: "arrow-left.svg",
    viewBox: "108 316 2083 1667",
    width: 2300,
    height: 2300,
  },
  hourglass: {
    file: "hourglass.svg",
    viewBox: "369 370 1422 1562",
    width: 2300,
    height: 2300,
  },
  warning: {
    file: "warning.svg",
    viewBox: "195 195 1910 1910",
    width: 2300,
    height: 2300,
  },
  user: {
    file: "user.svg",
    viewBox: "195 195 1910 1910",
    width: 2300,
    height: 2300,
  },
  globe: {
    file: "globe.svg",
    viewBox: "195 195 1910 1910",
    width: 2300,
    height: 2300,
  },
  camera: {
    file: "camera.svg",
    viewBox: "0 0 1400 1400",
    width: 1400,
    height: 1400,
  },
  heart: {
    file: "heart.svg",
    viewBox: "0 0 1260 1120",
    width: 1260,
    height: 1120,
  },
  qr: {
    file: "qr.svg",
    viewBox: "0 0 1260 1260",
    width: 1260,
    height: 1260,
  },
  shield: {
    file: "shield.svg",
    viewBox: "0 0 980 1400",
    width: 980,
    height: 1400,
  },
  wrench: {
    file: "wrench.svg",
    viewBox: "0 0 700 1680",
    width: 700,
    height: 1680,
  },
  bookmark: {
    file: "bookmark.svg",
    viewBox: "0 0 980 1120",
    width: 980,
    height: 1120,
  },
  star: {
    file: "star.svg",
    viewBox: "0 0 1540 1400",
    width: 1540,
    height: 1400,
  },
  smartphone: {
    file: "smartphone.svg",
    viewBox: "0 0 980 1540",
    width: 980,
    height: 1540,
  },
  smile: {
    file: "smile.svg",
    viewBox: "0 0 1540 1540",
    width: 1540,
    height: 1540,
  },
  sunglasses: {
    file: "sunglasses.svg",
    viewBox: "0 0 1540 1540",
    width: 1540,
    height: 1540,
  },
} as const;

export type PixelIconName = keyof typeof assets;
type IconProps = Omit<SVGProps<SVGSVGElement>, "name"> & { size?: number };

export function PixelIcon({
  name,
  size = 20,
  className = "",
  ...props
}: IconProps & { name: PixelIconName }) {
  const asset = assets[name];
  return (
    <svg
      {...props}
      width={size}
      height={size}
      viewBox={asset.viewBox}
      className={`pixel-icon pixel-icon--asset ${className}`}
      aria-hidden="true"
      focusable="false"
    >
      <image
        href={`/brand/bitpolito/${asset.file}`}
        width={asset.width}
        height={asset.height}
      />
    </svg>
  );
}

export const Bitcoin = (props: IconProps) => <PixelIcon name="bitcoin" {...props} />;
export const MessageSquare = (props: IconProps) => <PixelIcon name="message" {...props} />;
export const KeyRound = (props: IconProps) => <PixelIcon name="key" {...props} />;
export const LockKeyhole = (props: IconProps) => <PixelIcon name="lock" {...props} />;
export const Share2 = (props: IconProps) => <PixelIcon name="share" {...props} />;
export const Zap = (props: IconProps) => <PixelIcon name="lightning" {...props} />;
export const ArrowLeft = (props: IconProps) => <PixelIcon name="arrow-left" {...props} />;
export const ArrowRight = (props: IconProps) => <PixelIcon name="arrow-right" {...props} />;
export const Hourglass = (props: IconProps) => <PixelIcon name="hourglass" {...props} />;
export const AlertCircle = (props: IconProps) => <PixelIcon name="warning" {...props} />;
export const UserRound = (props: IconProps) => <PixelIcon name="user" {...props} />;
export const Globe = (props: IconProps) => <PixelIcon name="globe" {...props} />;
export const Camera = (props: IconProps) => <PixelIcon name="camera" {...props} />;
export const Heart = (props: IconProps) => <PixelIcon name="heart" {...props} />;
export const QrCode = (props: IconProps) => <PixelIcon name="qr" {...props} />;
export const Shield = (props: IconProps) => <PixelIcon name="shield" {...props} />;
export const Wrench = (props: IconProps) => <PixelIcon name="wrench" {...props} />;
export const Bookmark = (props: IconProps) => <PixelIcon name="bookmark" {...props} />;
export const Star = (props: IconProps) => <PixelIcon name="star" {...props} />;
export const Smartphone = (props: IconProps) => <PixelIcon name="smartphone" {...props} />;
export const EmojiSmile = (props: IconProps) => <PixelIcon name="smile" {...props} />;
export const EmojiCool = (props: IconProps) => <PixelIcon name="sunglasses" {...props} />;
